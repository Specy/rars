// Numbers as RARS prints and reads them on Java 21: every golden vector that
// fixtures/java-numbers/GoldenVectors.java wrote on JDK 21 goes through print float and double
// (2, 3) and read int, float and double (5, 6, 7), and must match, in RV32 and in RV64.
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { RISCV } from '../dist/index.mjs'

const vectors = JSON.parse(await readFile(new URL('./fixtures/java-numbers/vectors.json', import.meta.url), 'utf8'))
assert.match(vectors.java, /^21\./, 'the vectors come from Java 21')

// The IO handlers are shared by every core, so one set serves the whole file.
let printed = ''
let lines = []
const handlers = {
    printString: text => { printed += text },
    readInt: () => lines.shift(),
    readFloat: () => lines.shift(),
    readDouble: () => lines.shift(),
}

function core(source) {
    const riscv = RISCV.makeRiscVFromFiles({ 'main.s': source }, 'main.s')
    const result = riscv.assemble()
    assert.equal(result.hasErrors, false, result.report)
    for (const [name, handler] of Object.entries(handlers)) riscv.registerHandler(name, handler)
    return riscv
}

async function finish(riscv) {
    while (!riscv.terminated) await riscv.simulateWithLimit(1_000_000)
}

/** Little-endian bytes of 32 bit words. */
function wordBytes(words) {
    return words.flatMap(word => [word & 0xff, (word >>> 8) & 0xff, (word >>> 16) & 0xff, (word >>> 24) & 0xff])
}

/** The hexadecimal words, low word first, as a little-endian double lies in memory. */
function wordsOf(hex) {
    return hex.length === 8 ? [parseInt(hex, 16)] : [parseInt(hex.slice(8), 16), parseInt(hex.slice(0, 8), 16)]
}

function hexOf(bytes, offset, size) {
    const word = index => (bytes[offset + index] | (bytes[offset + index + 1] << 8) | (bytes[offset + index + 2] << 16) | (bytes[offset + index + 3] << 24)) >>> 0
    const hex = word => word.toString(16).padStart(8, '0')
    return size === 4 ? hex(word(0)) : hex(word(4)) + hex(word(0))
}

// Printing: a loop prints the s1 values stored at `values`, one per line.
async function checkPrinting(name, cases, load, service, size) {
    const riscv = core(`
        .data
        .align 3
values: .space ${cases.length * size}
        .text
main:   la    s0, values
loop:   beqz  s1, done
        ${load} fa0, 0(s0)
        li    a7, ${service}
        ecall
        li    a0, 10
        li    a7, 11
        ecall
        addi  s0, s0, ${size}
        addi  s1, s1, -1
        j     loop
done:   li    a7, 10
        ecall
`)
    riscv.initialize(true)
    riscv.setMemoryBytes(riscv.getAddressOfLabel('values'), wordBytes(cases.flatMap(([bits]) => wordsOf(bits))))
    riscv.setRegisterValue('s1', 0, cases.length)
    printed = ''
    await finish(riscv)
    const actual = printed.split('\n')
    assert.equal(actual.length, cases.length + 1)
    cases.forEach(([bits, expected], index) => assert.equal(actual[index], expected, `${name} of ${bits}`))
}

// Reading: a loop reads s1 lines and stores each value's bits at `results`. A line the reference
// rejects stops the program at the syscall, so each of those runs on its own.
const SERVICE = { parseInt: [5, 'sw a0', 4, 'integer'], parseFloat: [6, 'fsw fa0', 4, 'float'], parseDouble: [7, 'fsd fa0', 8, 'double'] }

async function checkReading(name) {
    const [service, store, size, kind] = SERVICE[name]
    const valid = vectors[name].filter(([, expected]) => expected !== null)
    const invalid = vectors[name].filter(([, expected]) => expected === null)
    const riscv = core(`
        .data
        .align 3
results: .space ${valid.length * size}
        .text
main:   la    s0, results
loop:   beqz  s1, done
        li    a7, ${service}
        ecall
        ${store}, 0(s0)
        addi  s0, s0, ${size}
        addi  s1, s1, -1
        j     loop
done:   li    a7, 10
        ecall
`)
    riscv.initialize(true)
    riscv.setRegisterValue('s1', 0, valid.length)
    lines = valid.map(([line]) => line)
    await finish(riscv)
    assert.equal(lines.length, 0, `${name}: every line was read`)
    const bytes = Array.from(riscv.readMemoryBytes(riscv.getAddressOfLabel('results'), valid.length * size))
    valid.forEach(([line, expected], index) => {
        const actual = hexOf(bytes, index * size, size)
        const wanted = typeof expected === 'number' ? (expected >>> 0).toString(16).padStart(8, '0') : expected
        assert.equal(actual, wanted, `${name} of ${JSON.stringify(line)}`)
    })

    const rejecting = core(`
        .text
main:   li    a7, ${service}
        ecall
        li    a7, 10
        ecall
`)
    const message = `Runtime exception at 0x00400004: invalid ${kind} input (syscall ${service})`
    for (const [line] of invalid) {
        rejecting.initialize(true)
        lines = [line]
        await assert.rejects(() => finish(rejecting), error => {
            assert.ok(String(error.message).includes(message), `${name} of ${JSON.stringify(line)}: ${error.message}`)
            return true
        })
    }
    return [valid.length, invalid.length]
}

// The simulator is a shared global, so the two width modes run in sequence.
for (const is64Bit of [false, true]) {
    RISCV.setIs64Bit(is64Bit)
    await checkPrinting('print float', vectors.floatToString, 'flw', 2, 4)
    await checkPrinting('print double', vectors.doubleToString, 'fld', 3, 8)
    const counts = {}
    for (const name of ['parseInt', 'parseFloat', 'parseDouble']) counts[name] = await checkReading(name)
    console.log(`ok - ${is64Bit ? 'RV64' : 'RV32'} Java 21 number text: ${vectors.floatToString.length} floats and ` +
        `${vectors.doubleToString.length} doubles printed, ` +
        Object.entries(counts).map(([name, [valid, invalid]]) => `${name} ${valid} read and ${invalid} rejected`).join(', '))
}
RISCV.setIs64Bit(false)
