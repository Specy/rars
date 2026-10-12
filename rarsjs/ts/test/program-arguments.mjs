// Program arguments: setProgramArguments gives every later initialize an argv, argv[0] included,
// stored above the stack with a0 = argc and a1 = argv, while sp keeps its initial value; pointers
// are four bytes on RV32 and eight on RV64; words that do not fit are refused with a sentence for
// the person; Undo to the start keeps them.
import assert from 'node:assert/strict'
import { RISCV, RISCV_REGISTERS, registerHandlers, unimplementedHandler } from '../dist/index.mjs'

const HANDLER_NAMES = [
    'openFile', 'closeFile', 'writeFile', 'readFile', 'confirm', 'inputDialog',
    'outputDialog', 'readDouble', 'readFloat', 'readInt', 'readString', 'readChar',
    'printString', 'sleep', 'time', 'stdIn', 'seekFile', 'stdOut', 'stdErr', 'randomSeed',
]
const STACK_POINTER = 0x7fffeffc
const STACK_BASE = 0x7ffffffc

// Prints argc, then each argv string on a line of its own, walking argv to its NULL. `load` and
// `size` read one pointer: lw and 4 on RV32, ld and 8 on RV64.
const printArgv = (load, size) => `
main:   mv   s0, a0
        mv   s1, a1
        li   a7, 1
        ecall
next:   ${load} t0, 0(s1)
        beqz t0, done
        li   a0, 10
        li   a7, 11
        ecall
        mv   a0, t0
        li   a7, 4
        ecall
        addi s1, s1, ${size}
        j    next
done:   li   a7, 10
        ecall`

/** An assembled core whose prints go to `printed`, with undo on, at the width set before. */
function core(source, printed = []) {
    const riscv = RISCV.makeRiscVFromFiles({ 'main.s': source }, 'main.s')
    riscv.setUndoSize(1000)
    const result = riscv.assemble()
    assert.equal(result.hasErrors, false, result.report)
    registerHandlers(riscv, {
        ...Object.fromEntries(HANDLER_NAMES.map(name => [name, unimplementedHandler(name)])),
        printString: text => { printed.push(text) },
    })
    return riscv
}

async function runToEnd(riscv) {
    while (!riscv.terminated) await riscv.simulateWithLimit(10_000)
}

const u32 = value => value >>> 0
/** A register's whole 64-bit value, as the decimal string the core reports. */
const long = (riscv, name) => Array.from(riscv.getRegistersValuesLong())[RISCV_REGISTERS.indexOf(name)]
const bytesAt = (riscv, address, length) => Uint8Array.from(riscv.readMemoryBytes(address, length))
const word = (riscv, address) => new DataView(bytesAt(riscv, address, 4).buffer).getUint32(0, true)
const doubleword = (riscv, address) => new DataView(bytesAt(riscv, address, 8).buffer).getBigUint64(0, true)
function cString(riscv, address) {
    const bytes = []
    for (let at = address; ; at++) {
        const [byte] = riscv.readMemoryBytes(at, 1)
        if (byte === 0) return new TextDecoder().decode(Uint8Array.from(bytes))
        bytes.push(byte)
    }
}

// 1. RV32: argc and argv in a0 and a1, every string printed by the program; sp where it starts.
{
    RISCV.setIs64Bit(false)
    const printed = []
    const riscv = core(printArgv('lw', 4), printed)
    riscv.setProgramArguments(['./main', 'hello', 'two words', 'héllo'])
    riscv.initialize(true)
    assert.equal(riscv.getRegisterValue('a0'), 4, 'argc counts argv[0]')
    assert.equal(u32(riscv.stackPointer), STACK_POINTER, 'sp keeps its initial value')
    await runToEnd(riscv)
    assert.equal(printed.join(''), '4\n./main\nhello\ntwo words\nhéllo')
}

// 2. RV32 layout: strings end at the stack base, argv[0] first; the 4-byte, NULL-terminated argv
//    array sits right below them, word-aligned.
{
    RISCV.setIs64Bit(false)
    const riscv = core('main: nop')
    riscv.setProgramArguments(['./main', 'hello', 'two words'])
    riscv.initialize(true)
    const argv = u32(riscv.getRegisterValue('a1'))
    assert.equal(argv, 0x7fffffd4)
    const pointers = [0, 1, 2, 3].map(i => word(riscv, argv + 4 * i))
    assert.deepEqual(pointers, [0x7fffffe5, 0x7fffffec, 0x7ffffff2, 0])
    assert.deepEqual(pointers.slice(0, 3).map(pointer => cString(riscv, pointer)), ['./main', 'hello', 'two words'])
    assert.deepEqual(Array.from(riscv.readMemoryBytes(STACK_POINTER, 16)), new Array(16).fill(0),
        'the 16 bytes above sp are left alone')
}

// 3. RV64: eight-byte pointers, aligned to eight, read with ld; argc and argv in the 64-bit a0, a1.
{
    RISCV.setIs64Bit(true)
    const printed = []
    const riscv = core(printArgv('ld', 8), printed)
    riscv.setProgramArguments(['./main', 'hello', 'two words'])
    riscv.initialize(true)
    assert.equal(long(riscv, 'a0'), '3')
    const argv = Number(long(riscv, 'a1'))
    // 23 bytes of strings from 0x7fffffe5; 4 pointers of 8 below them, aligned down to 8
    assert.equal(argv, 0x7fffffc0)
    const pointers = [0, 1, 2, 3].map(i => doubleword(riscv, argv + 8 * i))
    assert.deepEqual(pointers, [0x7fffffe5n, 0x7fffffecn, 0x7ffffff2n, 0n])
    assert.deepEqual(Array.from(riscv.readMemoryBytes(0x7fffffe0, 5)), [0, 0, 0, 0, 0], 'the padding is zero')
    assert.equal(riscv.stackPointerLong, String(STACK_POINTER))
    await runToEnd(riscv)
    assert.equal(printed.join(''), '3\n./main\nhello\ntwo words')

    // the same space, which eight-byte pointers fill sooner
    assert.equal(RISCV.programArgumentsSpace(), 4080)
    const fits32 = ['./main', 'x'.repeat(4080 - 12 - 7 - 1)]
    assert.throws(() => RISCV.checkProgramArguments(fits32),
        { message: 'Arguments take 4,092 bytes with the program name and argv; RISC-V programs have 4,080 bytes for them' })
    RISCV.checkProgramArguments(['./main', 'x'.repeat(4080 - 4 - 24 - 7 - 1)])
    RISCV.checkProgramArguments(fits32, false)
    RISCV.setIs64Bit(false)
    RISCV.checkProgramArguments(fits32)
    assert.throws(() => RISCV.checkProgramArguments(fits32, true), /4,092 bytes/, 'the width named, not the current one')
    assert.throws(() => RISCV.checkProgramArguments(fits32, 'yes'), TypeError)
}

// 4. Never set, or set to none: nothing is stored and a0, a1 start at 0, as before.
{
    RISCV.setIs64Bit(false)
    for (const argv of [undefined, []]) {
        const riscv = core('main: nop')
        if (argv) riscv.setProgramArguments(argv)
        riscv.initialize(true)
        assert.deepEqual([riscv.getRegisterValue('a0'), riscv.getRegisterValue('a1')], [0, 0])
        assert.deepEqual(Array.from(riscv.readMemoryBytes(STACK_BASE - 16, 16)), new Array(16).fill(0))
    }
}

// 5. Refusals, with a sentence for the person; the argv set before stays.
{
    RISCV.setIs64Bit(false)
    const riscv = core('main: nop')
    riscv.setProgramArguments(['./main', 'kept'])
    const tooLong = ['./main', 'x'.repeat(4080 - 12 - 7)]
    assert.throws(() => riscv.setProgramArguments(tooLong),
        { message: 'Arguments take 4,084 bytes with the program name and argv; RISC-V programs have 4,080 bytes for them' })
    assert.throws(() => riscv.setProgramArguments(['./main', ...new Array(1025).fill('')]),
        { message: 'Arguments may contain at most 1,024 words' })
    assert.throws(() => riscv.setProgramArguments(['./main', 'a\0b']), { message: 'Arguments cannot contain NUL' })
    assert.throws(() => riscv.setProgramArguments(['./main', 1]), { name: 'TypeError', message: 'Arguments must be strings' })
    riscv.initialize(true)
    assert.equal(riscv.getRegisterValue('a0'), 2, 'a refused argv leaves the one set before')
    assert.equal(cString(riscv, word(riscv, u32(riscv.getRegisterValue('a1')) + 4)), 'kept')
}

// 6. Every initialize stores them again, over a program that wrote on them; Undo back to the
//    start keeps them, since they are no step of the history.
for (const is64Bit of [false, true]) {
    RISCV.setIs64Bit(is64Bit)
    const size = is64Bit ? 8 : 4
    const riscv = core(`
main:   ${is64Bit ? 'ld' : 'lw'} t0, ${size}(a1)
        sb   zero, 0(t0)
        ${is64Bit ? 'sd' : 'sw'} zero, 0(a1)
        li   a0, 99
        li   a1, 98
        addi sp, sp, -16`)
    riscv.setProgramArguments(['./main', 'abc'])
    riscv.initialize(true)
    const argv = u32(riscv.getRegisterValue('a1'))
    const pointer = index => is64Bit ? Number(doubleword(riscv, argv + 8 * index)) : word(riscv, argv + 4 * index)
    assert.equal(riscv.canUndo, false, 'storing them is no step')
    await runToEnd(riscv)
    assert.equal(pointer(0), 0, 'the program overwrote argv[0]')
    while (riscv.canUndo) riscv.undo()
    assert.deepEqual([riscv.getRegisterValue('a0'), u32(riscv.getRegisterValue('a1')), u32(riscv.stackPointer)],
        [2, argv, STACK_POINTER], `undo to the start (${is64Bit ? 'RV64' : 'RV32'})`)
    assert.deepEqual([cString(riscv, pointer(0)), cString(riscv, pointer(1))], ['./main', 'abc'])

    await runToEnd(riscv)
    riscv.initialize(true)
    assert.deepEqual([riscv.getRegisterValue('a0'), u32(riscv.getRegisterValue('a1'))], [2, argv])
    assert.deepEqual([cString(riscv, pointer(0)), cString(riscv, pointer(1))], ['./main', 'abc'],
        `initialize stores them again (${is64Bit ? 'RV64' : 'RV32'})`)
}
RISCV.setIs64Bit(false)
