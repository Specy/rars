// Text is UTF-8 throughout, as RARS has it: string literals are stored by code point (RARS encoded
// one UTF-16 unit at a time and stored an emoji as "??"), and print string, read string, the
// dialogs and open read and write UTF-8 the way Java 21 decodes and encodes it (golden vectors
// from fixtures/java-numbers/GoldenVectors.java). Print char and read char work as RARS does.
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { RISCV, registerHandlers, unimplementedHandler } from '../dist/index.mjs'

const vectors = JSON.parse(await readFile(new URL('./fixtures/java-numbers/vectors.json', import.meta.url), 'utf8'))
assert.match(vectors.java, /^21\./, 'the vectors come from Java 21')

const HANDLER_NAMES = [
    'openFile', 'closeFile', 'writeFile', 'readFile', 'confirm', 'inputDialog',
    'outputDialog', 'readDouble', 'readFloat', 'readInt', 'readString', 'readChar',
    'printString', 'sleep', 'time', 'stdIn', 'seekFile', 'stdOut', 'stdErr', 'randomSeed',
]

function assemble(source) {
    const riscv = RISCV.makeRiscVFromFiles({ 'main.s': source }, 'main.s')
    const result = riscv.assemble()
    assert.equal(result.hasErrors, false, result.errors.map(e => `${e.sourceLine}: ${e.message}`).join('\n'))
    return riscv
}

/** Runs a program with only `handlers` implemented; any other handler call fails the run. */
async function run(source, handlers = {}) {
    const riscv = assemble(source)
    registerHandlers(riscv, {
        ...Object.fromEntries(HANDLER_NAMES.map(name => [name, unimplementedHandler(name)])),
        ...handlers,
    })
    riscv.initialize(true)
    while (!riscv.terminated) await riscv.simulateWithLimit(100_000)
    return riscv
}

const bytesAt = (riscv, label, length) => Array.from(riscv.readMemoryBytes(riscv.getAddressOfLabel(label), length))
const register = (riscv, name) => Number(BigInt.asIntN(32, BigInt(riscv.getRegistersValuesLong()[NAMES.indexOf(name)])))
const NAMES = ['zero', 'ra', 'sp', 'gp', 'tp', 't0', 't1', 't2', 's0', 's1', 'a0', 'a1', 'a2', 'a3', 'a4', 'a5', 'a6', 'a7']
const TEXT = 'é€😀'
const TEXT_BYTES = [0xc3, 0xa9, 0xe2, 0x82, 0xac, 0xf0, 0x9f, 0x98, 0x80]

try {
    for (const is64Bit of [false, true]) {
        RISCV.setIs64Bit(is64Bit)

        // Literals are stored by code point: an emoji is its four bytes, not "??", whether it is
        // written as itself or as the two \u escapes of its surrogate pair.
        {
            const riscv = assemble(`
        .data
written: .asciz "${TEXT}"
escaped: .string "\\u00e9\\u20AC\\ud83d\\ude00"
listed:  .ascii "é", "€"
         .ascii "😀"
         .byte 0
signed:  .asciz "\\u+041\\uff41"
lone:    .asciz "a\\ud83dz"
        .text
main:   li a7, 10
        ecall
`)
            assert.deepEqual(bytesAt(riscv, 'written', 10), [...TEXT_BYTES, 0])
            assert.deepEqual(bytesAt(riscv, 'escaped', 10), [...TEXT_BYTES, 0])
            assert.deepEqual(bytesAt(riscv, 'listed', 10), [...TEXT_BYTES, 0], 'a list and a continuation line')
            assert.deepEqual(bytesAt(riscv, 'signed', 5), [0x41, 0xef, 0xbd, 0x81, 0], 'Integer.parseInt reads the digits')
            assert.deepEqual(bytesAt(riscv, 'lone', 4), [0x61, 0x3f, 0x7a, 0], 'an unpaired surrogate is a question mark')
            assert.equal(riscv.getAddressOfLabel('escaped'), riscv.getAddressOfLabel('written') + 10)
        }
        for (const [literal, message] of [
            ['"\\u12"', /unicode escape "\\u12" is incomplete\. Only escapes with 4 digits are valid\./],
            ['"\\u12g4"', /illegal unicode escape: "\\u12g4"/],
            // RARS's Character.toChars threw on a negative unit, which stopped the assembler.
            ['"\\u-001"', /illegal unicode escape: "\\u-001"/],
        ]) {
            const result = RISCV.makeRiscVFromFiles({ 'main.s': `.data\n.asciz ${literal}\n` }, 'main.s').assemble()
            assert.equal(result.hasErrors, true, literal)
            assert.match(result.errors.map(e => e.message).join('\n'), message, literal)
        }

        // A round trip: print string, read string and a file path all carry the literal's text.
        {
            const printed = []
            const opened = []
            const riscv = await run(`
        .data
text:   .asciz "${TEXT}"
path:   .asciz "données/${TEXT}.txt"
buffer: .space 32
cut:    .space 8
        .text
main:   la   a0, text
        li   a7, 4
        ecall
        la   a0, buffer
        li   a1, 32
        li   a7, 8
        ecall
        la   a0, buffer
        li   a7, 4
        ecall
        la   a0, cut
        li   a1, 4
        li   a7, 8
        ecall
        la   a0, cut
        li   a7, 4
        ecall
        la   a0, path
        li   a1, 0
        li   a7, 1024
        ecall
        li   a7, 10
        ecall
`, {
                printString: text => printed.push(text),
                readString: () => TEXT,
                openFile: (path, flags, append) => { opened.push([path, flags, append]); return 3 },
            })
            assert.deepEqual(bytesAt(riscv, 'buffer', 11), [...TEXT_BYTES, 0x0a, 0], 'read string stores UTF-8, then the newline')
            assert.deepEqual(bytesAt(riscv, 'cut', 4), [0xc3, 0xa9, 0xe2, 0], 'a buffer of 4 keeps 3 bytes: a character is cut')
            assert.deepEqual(printed, [TEXT, `${TEXT}\n`, 'é\ufffd'])
            assert.deepEqual(opened, [[`données/${TEXT}.txt`, 0, false]])
        }

        // Print char prints the character numbered by the low byte of a0, and read char answers the
        // first UTF-16 unit of what was typed, as RARS does.
        {
            const printed = []
            const answers = ['é', '€', '😀', 'A']
            const riscv = await run(`
        .text
main:   li   a0, 0x41
        li   a7, 11
        ecall
        li   a0, 0xe9
        ecall
        li   a0, 0xc3
        ecall
        li   a0, 0x20ac
        ecall
        li   a0, 0x1f600
        ecall
        li   a7, 12
        ecall
        mv   t0, a0
        ecall
        mv   t1, a0
        ecall
        mv   t2, a0
        ecall
        mv   s0, a0
        li   a7, 10
        ecall
`, { printString: text => printed.push(text), readChar: () => answers.shift() })
            assert.deepEqual(printed, ['A', 'é', 'Ã', '¬', '\u0000'])
            assert.deepEqual(['t0', 't1', 't2', 's0'].map(name => register(riscv, name)), [0xe9, 0x20ac, 0xd83d, 0x41])
        }

        // The dialogs read their message from a0 as UTF-8, the double ones included, and input
        // dialog string (54) stores its answer as UTF-8, measured in bytes.
        {
            const messages = []
            const answers = ['é€', 'é', '2.5']
            const riscv = await run(`
        .data
ask:    .asciz "${TEXT}?"
other:  .asciz "not the message"
tail:   .asciz " ok"
small:  .space 4
large:  .space 8
half:   .double 0.5
        .text
main:   la   a0, ask
        li   a7, 50
        ecall
        la   a0, ask
        la   a1, small
        li   a2, 4
        li   a7, 54
        ecall
        mv   s0, a1
        la   a0, ask
        la   a1, large
        li   a2, 8
        li   a7, 54
        ecall
        mv   s1, a1
        la   tp, other
        la   a0, ask
        li   a7, 53
        ecall
        mv   t0, a1
        la   a0, ask
        la   t1, half
        fld  fa0, 0(t1)
        li   a7, 58
        ecall
        la   a0, ask
        la   a1, tail
        li   a7, 59
        ecall
        li   a7, 10
        ecall
`, {
                confirm: message => { messages.push(['confirm', message]); return 0 },
                inputDialog: message => { messages.push(['input', message]); return answers.shift() },
                outputDialog: (message, type) => { messages.push(['output', message, type]) },
            })
            assert.deepEqual(messages, [
                ['confirm', `${TEXT}?`], ['input', `${TEXT}?`], ['input', `${TEXT}?`], ['input', `${TEXT}?`],
                ['output', `${TEXT}?0.5`, 1], ['output', `${TEXT}? ok`, 1],
            ])
            assert.deepEqual(bytesAt(riscv, 'small', 4), [0xc3, 0xa9, 0xe2, 0], 'three bytes of "é€", cut, then the NUL')
            assert.equal(register(riscv, 's0'), -4, 'the answer did not fit')
            assert.deepEqual(bytesAt(riscv, 'large', 4), [0xc3, 0xa9, 0x0a, 0])
            assert.equal(register(riscv, 's1'), 0)
            assert.equal(register(riscv, 't0'), 0, 'input dialog double read its message from a0 and parsed 2.5')
        }

        // A long string prints whole: RARS has no limit on print string.
        {
            const text = 'é'.repeat(40000)
            const printed = []
            await run(`.data\ntext: .asciz "${text}"\n.text\nmain: la a0, text\nli a7, 4\necall\nli a7, 10\necall\n`,
                { printString: value => printed.push(value) })
            assert.deepEqual(printed, [text])
        }

        // Every Java 21 decoding of the golden vectors through print string, malformed bytes included.
        {
            const decodings = vectors.utf8Decode
            const lines = ['.data', `table: .word ${decodings.map((_, index) => `str${index}`).join(', ')}`]
            decodings.forEach(([hex], index) => {
                lines.push(`str${index}: .byte ${[...Buffer.from(hex, 'hex')].join(', ')}, 0`)
            })
            lines.push('.text', 'main: la s0, table', `li s1, ${decodings.length}`,
                'loop: lw a0, 0(s0)', 'li a7, 4', 'ecall', 'addi s0, s0, 4', 'addi s1, s1, -1', 'bnez s1, loop',
                'li a7, 10', 'ecall')
            const printed = []
            await run(lines.join('\n'), { printString: text => printed.push(text) })
            assert.equal(printed.length, decodings.length)
            decodings.forEach(([hex, text], index) => assert.equal(printed[index], text, `bytes ${hex}`))
        }

        // Every Java 21 encoding of the golden vectors through read string, unpaired surrogates included.
        {
            const encodings = vectors.utf8Encode
            const answers = encodings.map(([text]) => text)
            const riscv = await run([
                '.data', `buffers: .space ${64 * encodings.length}`, '.text', 'main: la s0, buffers', `li s1, ${encodings.length}`,
                'loop: mv a0, s0', 'li a1, 64', 'li a7, 8', 'ecall', 'addi s0, s0, 64', 'addi s1, s1, -1',
                'bnez s1, loop', 'li a7, 10', 'ecall',
            ].join('\n'), { readString: () => answers.shift() })
            encodings.forEach(([text, hex], index) => {
                const expected = [...Buffer.from(hex, 'hex'), 0x0a, 0]
                const address = riscv.getAddressOfLabel('buffers') + 64 * index
                assert.deepEqual(Array.from(riscv.readMemoryBytes(address, expected.length)), expected, JSON.stringify(text))
            })
        }

        console.log(`ok - RV${is64Bit ? 64 : 32} text as UTF-8: literals by code point, print and read string, open, dialogs, print and read char, ${vectors.utf8Decode.length} Java decodings and ${vectors.utf8Encode.length} encodings`)
    }
} finally {
    RISCV.setIs64Bit(false)
}
