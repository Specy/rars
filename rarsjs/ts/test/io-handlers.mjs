// The IO handler contract: print services reach printString as formatted text, reads hand over
// raw text that the syscalls parse, dialogs answer null for Cancel, file writes report their
// count, and bytes cross as plain arrays of numbers.
import assert from 'node:assert/strict'
import { RISCV, registerHandlers, unimplementedHandler } from '../dist/index.mjs'

const HANDLER_NAMES = [
    'openFile', 'closeFile', 'writeFile', 'readFile', 'confirm', 'inputDialog',
    'outputDialog', 'readDouble', 'readFloat', 'readInt', 'readString', 'readChar',
    'printString', 'sleep', 'time', 'stdIn', 'seekFile', 'stdOut', 'stdErr', 'randomSeed',
]

/** Runs a program with only `handlers` implemented; any other handler call fails the run. */
async function run(source, handlers) {
    const riscv = RISCV.makeRiscVFromFiles({ 'main.s': source }, 'main.s')
    const result = riscv.assemble()
    assert.equal(result.hasErrors, false, result.report)
    registerHandlers(riscv, {
        ...Object.fromEntries(HANDLER_NAMES.map(name => [name, unimplementedHandler(name)])),
        ...handlers,
    })
    riscv.initialize(true)
    while (!riscv.terminated) await riscv.simulateWithLimit(10_000)
    return riscv
}

const register = (riscv, name) => riscv.getRegisterValue(name)

// Every print service writes its formatted text through printString, one call per syscall.
{
    const printed = []
    await run(`
        .data
text:   .asciz "two\\twords"
one:    .float 1.0
small:  .double 1e-5
        .text
main:   li   a0, -42
        li   a7, 1
        ecall
        li   a0, 65
        li   a7, 11
        ecall
        la   a0, text
        li   a7, 4
        ecall
        li   a0, -1
        li   a7, 34
        ecall
        li   a0, 5
        li   a7, 35
        ecall
        li   a0, -1
        li   a7, 36
        ecall
        flw  fa0, one, t0
        li   a7, 2
        ecall
        fld  fa0, small, t0
        li   a7, 3
        ecall
        li   a7, 10
        ecall
`, { printString: text => printed.push(text) })
    assert.deepEqual(printed, ['-42', 'A', 'two\twords', '0xffffffff', '00000000000000000000000000000101',
        '4294967295', '1.0', '1.0E-5'])
}

// Read char takes the first character of the answer, so Enter alone reads as 10.
{
    const answers = ['\n', 'abc', 'é']
    const riscv = await run(`
        .text
main:   li   a7, 12
        ecall
        mv   s0, a0
        li   a7, 12
        ecall
        mv   s1, a0
        li   a7, 12
        ecall
        mv   s2, a0
        li   a7, 10
        ecall
`, { readChar: (...args) => { assert.deepEqual(args, []); return answers.shift() } })
    assert.deepEqual([register(riscv, 's0'), register(riscv, 's1'), register(riscv, 's2')], [10, 97, 0xe9])
    // An empty answer is what RARS rejects: nothing was typed.
    await assert.rejects(() => run(`
        .text
main:   li   a7, 12
        ecall
`, { readChar: () => '' }), /Runtime exception at 0x00400004: invalid char input \(syscall 12\)/)
    // A read handler must answer with text.
    await assert.rejects(() => run(`
        .text
main:   li   a7, 5
        ecall
`, { readInt: () => 5 }), /Handler readInt did not return a string/)
}

// Dialogs: null is Cancel for the input dialogs (status -2), confirm forwards 2, the syscalls
// parse the text as RARS does, and the program waits for a message dialog to settle.
{
    const inputs = [null, ' 5', '-17', null, ' 0x1p3f ', null, null]
    const messages = []
    const settled = []
    const riscv = await run(`
        .data
msg:    .asciz "Value? "
buffer: .space 16
small:  .float 0.001
tiny:   .double 1e-5
        .text
main:   la   a0, msg
        li   a7, 51
        ecall
        mv   s0, a0
        mv   s1, a1
        la   a0, msg
        li   a7, 51
        ecall
        mv   s2, a1
        la   a0, msg
        li   a7, 51
        ecall
        mv   s3, a0
        mv   s4, a1
        la   a0, msg
        li   a7, 52
        ecall
        mv   s5, a1
        la   a0, msg
        li   a7, 52
        ecall
        fmv.x.w s6, ft0
        mv   s7, a1
        la   a0, msg
        mv   tp, a0          # RARS 1.6 reads this dialog's message from x4
        li   a7, 53
        ecall
        mv   s8, a1
        la   a0, msg
        la   a1, buffer
        li   a2, 16
        li   a7, 54
        ecall
        mv   s9, a1
        la   a0, msg
        li   a7, 50
        ecall
        mv   s10, a0
        la   a0, msg
        flw  fa1, small, t0
        li   a7, 60
        ecall
        la   a0, msg
        fld  fa0, tiny, t0
        li   a7, 58
        ecall
        li   a7, 10
        ecall
`, {
        inputDialog: message => { assert.equal(message, 'Value? '); return inputs.shift() },
        confirm: () => 2,
        outputDialog: (message, type) => {
            assert.equal(settled.length, messages.length, 'the previous message dialog settled first')
            messages.push([message, type])
            return new Promise(resolve => setTimeout(() => { settled.push(message); resolve() }, 5))
        },
    })
    assert.equal(inputs.length, 0)
    assert.deepEqual([register(riscv, 's0'), register(riscv, 's1')], [0, -2], 'int dialog: Cancel')
    assert.equal(register(riscv, 's2'), -1, 'int dialog: Integer.parseInt does not trim')
    assert.deepEqual([register(riscv, 's3'), register(riscv, 's4')], [-17, 0], 'int dialog: a number')
    assert.equal(register(riscv, 's5'), -2, 'float dialog: Cancel')
    assert.deepEqual([register(riscv, 's6'), register(riscv, 's7')], [0x41000000, 0], 'float dialog: Java grammar')
    assert.equal(register(riscv, 's8'), -2, 'double dialog: Cancel')
    assert.equal(register(riscv, 's9'), -2, 'string dialog: Cancel')
    assert.equal(register(riscv, 's10'), 2, 'confirm: Cancel')
    assert.deepEqual(messages, [['Value? 0.001', 1], ['Value? 1.0E-5', 1]])
    assert.equal(settled.length, 2)
    // undefined is not an answer: only null cancels.
    await assert.rejects(() => run(`
        .data
msg:    .asciz "?"
        .text
main:   la   a0, msg
        li   a7, 51
        ecall
`, { inputDialog: () => undefined }), /Handler inputDialog did not return a string or null/)
}

// Files and standard streams: bytes cross as plain arrays of numbers from 0 to 255, a write
// reports its count or -1, a read answers [count, bytes] with -1 for a failure.
{
    const calls = []
    const writes = [5, -1]
    const reads = [[3, [0, 128, 255]], [-1, []]]
    const riscv = await run(`
        .data
name:   .asciz "data.bin"
bytes:  .byte 104, 105, 0, 233, 255
buffer: .space 8
        .text
main:   la   a0, name
        li   a1, 1
        li   a7, 1024
        ecall
        mv   s0, a0
        mv   a0, s0
        la   a1, bytes
        li   a2, 5
        li   a7, 64
        ecall
        mv   s1, a0
        mv   a0, s0
        la   a1, bytes
        li   a2, 5
        li   a7, 64
        ecall
        mv   s2, a0
        mv   a0, s0
        la   a1, buffer
        li   a2, 8
        li   a7, 63
        ecall
        mv   s3, a0
        mv   a0, s0
        la   a1, buffer
        li   a2, 8
        li   a7, 63
        ecall
        mv   s4, a0
        li   a0, 1
        la   a1, bytes
        li   a2, 2
        li   a7, 64
        ecall
        mv   s5, a0
        li   a0, 2
        la   a1, bytes
        li   a2, 5
        li   a7, 64
        ecall
        mv   a0, s0
        li   a7, 57
        ecall
        li   a7, 10
        ecall
`, {
        openFile: (...args) => { calls.push(['openFile', ...args]); return 3 },
        writeFile: (...args) => { calls.push(['writeFile', ...args]); return writes.shift() },
        readFile: (...args) => { calls.push(['readFile', ...args]); return reads.shift() },
        stdOut: (...args) => { calls.push(['stdOut', ...args]) },
        stdErr: (...args) => { calls.push(['stdErr', ...args]) },
        closeFile: (...args) => { calls.push(['closeFile', ...args]) },
    })
    for (const [name, ...args] of calls) {
        for (const argument of args) {
            if (typeof argument === 'object') {
                assert.ok(Array.isArray(argument) && argument.every(byte => Number.isInteger(byte) && byte >= 0 && byte <= 255),
                    `${name} receives bytes as numbers from 0 to 255: ${JSON.stringify(argument)}`)
            }
        }
    }
    assert.deepEqual(calls, [
        ['openFile', 'data.bin', 1, false],
        ['writeFile', 3, [104, 105, 0, 233, 255]],
        ['writeFile', 3, [104, 105, 0, 233, 255]],
        ['readFile', 3, 8],
        ['readFile', 3, 8],
        ['stdOut', [104, 105]],
        ['stdErr', [104, 105, 0, 233, 255]],
        ['closeFile', 3],
    ])
    assert.equal(register(riscv, 's1'), 5, 'a write reports the count the handler answered')
    assert.equal(register(riscv, 's2'), -1, 'a failed write reports -1')
    assert.equal(register(riscv, 's3'), 3, 'a read reports its count')
    assert.deepEqual(Array.from(riscv.readMemoryBytes(riscv.getAddressOfLabel('buffer'), 3)), [0, 128, 255])
    assert.equal(register(riscv, 's4'), -1, 'a failed read reports -1, not the end of the file')
    assert.equal(register(riscv, 's5'), 2, 'standard output reports its count')
}

// GetCWD (17) writes the working directory, "/", the root that file paths resolve against, and
// leaves a0 at the buffer; a buffer too small for it and its NUL gets -1. RARS answers the JVM's
// user.dir, which a browser does not have.
for (const is64Bit of [false, true]) {
    RISCV.setIs64Bit(is64Bit)
    const riscv = await run(`
        .data
cwd:    .space 8
tiny:   .space 1
        .text
main:   la   a0, cwd
        li   a1, 8
        li   a7, 17
        ecall
        mv   s0, a0
        la   a0, tiny
        li   a1, 1
        li   a7, 17
        ecall
        mv   s1, a0
        li   a7, 10
        ecall
`, {})
    assert.deepEqual(Array.from(riscv.readMemoryBytes(riscv.getAddressOfLabel('cwd'), 2)), [0x2f, 0])
    assert.equal(Number(riscv.getRegistersValuesLong()[8]), riscv.getAddressOfLabel('cwd'), 'a0 is left at the buffer')
    assert.equal(Number(riscv.getRegistersValuesLong()[9]), -1, 'the path and its NUL do not fit in one byte')
    assert.deepEqual(Array.from(riscv.readMemoryBytes(riscv.getAddressOfLabel('tiny'), 1)), [0], 'and nothing is written')
}

// The time CSR reads the program time from the time handler, as the time service (30) does, so a
// scripted run's virtual clock reaches it: here sleeping advances the clock by what it asked for.
for (const is64Bit of [false, true]) {
    RISCV.setIs64Bit(is64Bit)
    let clock = 0x1_0000_1000
    const riscv = await run(`
        .text
main:   rdtime t0
        li   a0, 25
        li   a7, 32
        ecall
        rdtime t1
        ${is64Bit ? 'li t2, 0' : 'rdtimeh t2'}
        li   a7, 30
        ecall
        sub  s0, t1, t0
        li   a7, 10
        ecall
`, {
        sleep: milliseconds => { clock += milliseconds },
        time: () => clock,
    })
    // What the program's instructions see: RV32 reads a register's low 32 bits.
    const registers = riscv.getRegistersValuesLong().map(value => BigInt.asIntN(is64Bit ? 64 : 32, BigInt(value)))
    const low = value => BigInt.asIntN(is64Bit ? 64 : 32, value)
    assert.equal(registers[5], low(0x1_0000_1000n), 'the time CSR before the sleep')
    assert.equal(registers[6], low(0x1_0000_1019n), 'and 25 milliseconds later')
    if (!is64Bit) assert.equal(registers[7], 1n, 'timeh is the upper half of the same clock')
    assert.equal(registers[8], 25n)
    assert.deepEqual([registers[10], registers[11]].map(value => BigInt.asUintN(32, value)), [0x1019n, 1n],
        'the time service reads the same clock')
}
RISCV.setIs64Bit(false)

console.log('ok - IO handlers: print text, raw reads, read char, dialog Cancel, file counts and failures, bytes as numbers, GetCWD, the time CSR')
