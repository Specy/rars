// MidiOut (syscall 31) and MidiOutSync (33): the arguments reach the midiOut handler
// already in RARS's ranges, 33 is midiOut followed by a sleep of the same duration, and a promise
// from midiOut holds the program as any handler's does.
import assert from 'node:assert/strict'
import { RISCV, StopReason, registerHandlers, unimplementedHandler } from '../dist/index.mjs'

const HANDLER_NAMES = [
    'openFile', 'closeFile', 'writeFile', 'readFile', 'confirm', 'inputDialog',
    'outputDialog', 'readDouble', 'readFloat', 'readInt', 'readString', 'readChar',
    'printString', 'sleep', 'midiOut', 'time', 'stdIn', 'seekFile', 'stdOut', 'stdErr',
    'randomSeed',
]
const TEXT = 0x00400000

/** A core for the source lines with only `handlers` implemented, initialized. */
function core(lines, handlers) {
    const riscv = RISCV.makeRiscVFromFiles({ 'main.asm': lines.join('\n') }, 'main.asm')
    riscv.setUndoSize(1000)
    const result = riscv.assemble()
    assert.equal(result.hasErrors, false, result.report)
    registerHandlers(riscv, {
        ...Object.fromEntries(HANDLER_NAMES.map(name => [name, unimplementedHandler(name)])),
        ...handlers,
    })
    riscv.initialize(true)
    return riscv
}

/** The lines that play one tone, [pitch, duration, instrument, volume], with `service`. */
const tone = (service, [pitch, duration, instrument, volume]) => [
    `li a0, ${pitch}`, `li a1, ${duration}`, `li a2, ${instrument}`, `li a3, ${volume}`,
    `li a7, ${service}`, 'ecall',
]
const PRINT_7 = ['li a0, 7', 'li a7, 1', 'ecall']
const EXIT = ['li a7, 10', 'ecall']

/** Runs the lines to their exit and answers the midiOut and sleep calls made, in order. */
async function calls(lines) {
    const made = []
    const riscv = core(['main:', ...lines, ...EXIT], {
        midiOut: (...args) => { made.push(['midiOut', ...args]) },
        sleep: (milliseconds) => { made.push(['sleep', milliseconds]) },
    })
    assert.equal(await riscv.simulate(), StopReason.NORMAL_TERMINATION)
    return made
}

const BASE = [64, 500, 3, 80]
// Each argument at both ends of its range and one past each, the others in range:
// [argument, value in the register, what the handler receives].
const CASES = [
    [0, -1, 60], [0, 0, 0], [0, 127, 127], [0, 128, 60],
    [1, -1, 1000], [1, 0, 0], [1, 2147483647, 2147483647], [1, -2147483648, 1000],
    [2, -1, 0], [2, 0, 0], [2, 127, 127], [2, 128, 0],
    [3, -1, 100], [3, 0, 0], [3, 127, 127], [3, 128, 100],
]
const withArgument = (argument, value) => BASE.map((base, index) => index === argument ? value : base)
const given = CASES.map(([argument, value]) => withArgument(argument, value))
const received = CASES.map(([argument, , value]) => withArgument(argument, value))

// 1. Syscall 31 hands each tone to midiOut with every argument outside its range put back to
// RARS's default, and never sleeps.
{
    const made = await calls(given.flatMap(args => tone(31, args)))
    assert.deepEqual(made, received.map(args => ['midiOut', ...args]))
}

// 2. Syscall 33 is midiOut then sleep for the duration midiOut received, once each and in that order.
{
    const made = await calls(given.flatMap(args => tone(33, args)))
    assert.deepEqual(made, received.flatMap(args => [['midiOut', ...args], ['sleep', args[1]]]))
}

/**
 * Runs a tone with `service` and a print after it, with midiOut answering a promise, and checks that
 * nothing after midiOut runs until the promise is resolved.
 */
async function heldByPromise(service, afterRelease) {
    const made = []
    let release
    const riscv = core(['main:', ...tone(service, BASE), ...PRINT_7, ...EXIT], {
        midiOut: (...args) => {
            made.push(['midiOut', ...args])
            return new Promise(resolve => { release = resolve })
        },
        sleep: (milliseconds) => { made.push(['sleep', milliseconds]) },
        printString: (text) => { made.push(['printString', text]) },
    })
    let settled = false
    const run = riscv.simulate().then(reason => { settled = true; return reason })
    await new Promise(resolve => setTimeout(resolve, 20))
    assert.equal(settled, false, `syscall ${service}: the run waits for the promise`)
    assert.deepEqual(made, [['midiOut', ...BASE]], `syscall ${service}: nothing after midiOut has run`)
    release()
    assert.equal(await run, StopReason.NORMAL_TERMINATION)
    assert.deepEqual(made, [['midiOut', ...BASE], ...afterRelease, ['printString', '7']])
}

// 3. A promise from midiOut suspends the run until it settles, on 31 and on 33, where the sleep
// waits for it too.
await heldByPromise(31, [])
await heldByPromise(33, [['sleep', BASE[1]]])

// 4. A pause from the sleep handler of a 33 ends the call after the ecall, as it does for a 32.
{
    const LOOP = ['main: li t0, 0', 'loop: addi t0, t0, 1', ...tone(33, BASE), 'next: j loop']
    const ECALL = TEXT + 28
    const NEXT = TEXT + 32
    const made = []
    const riscv = core(LOOP, {
        midiOut: (...args) => { made.push(['midiOut', ...args]) },
        sleep: (milliseconds) => { made.push(['sleep', milliseconds]); riscv.pause() },
    })
    assert.equal(await riscv.simulateWithLimit(1000), StopReason.PAUSE)
    assert.equal(riscv.getLastRunInstructionCount(), 8, 'li, addi, five li and the ecall')
    assert.equal(riscv.programCounter, NEXT, 'the ecall has run, the instruction after it has not')
    assert.equal(riscv.terminated, false)
    assert.deepEqual(made, [['midiOut', ...BASE], ['sleep', BASE[1]]])

    // the next call resumes there and runs one more iteration up to the next 33
    assert.equal(await riscv.simulateWithLimit(1000), StopReason.PAUSE)
    assert.equal(riscv.getLastRunInstructionCount(), 8, 'j, addi, five li and the ecall')
    assert.equal(riscv.getRegisterValue('t0'), 2)
    assert.equal(made.length, 4)

    riscv.undo()
    assert.equal(riscv.programCounter, ECALL, 'undo puts the program back on the ecall')
}

console.log('midi: ok')
