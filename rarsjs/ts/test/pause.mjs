// A handler ending the run call it runs in with `pause()`, and the instruction count every run call
// reports: what a host needs to end its call where a program's frame ends, at a sleep, and to know
// how far it got.
import assert from 'node:assert/strict'
import { RISCV, StopReason, isRuntimeError, registerHandlers, unimplementedHandler } from '../dist/index.mjs'

const HANDLER_NAMES = [
    'openFile', 'closeFile', 'writeFile', 'readFile', 'confirm', 'inputDialog',
    'outputDialog', 'readDouble', 'readFloat', 'readInt', 'readString', 'readChar',
    'printString', 'sleep', 'time', 'stdIn', 'seekFile', 'stdOut', 'stdErr', 'randomSeed',
]
const TEXT = 0x00400000

/** A core for the source lines whose sleep pauses the call, initialized. */
function core(lines, sleep = (riscv) => riscv.pause()) {
    const riscv = RISCV.makeRiscVFromFiles({ 'main.asm': lines.join('\n') }, 'main.asm')
    riscv.setUndoSize(1000)
    const result = riscv.assemble()
    assert.equal(result.hasErrors, false, result.report)
    registerHandlers(riscv, {
        ...Object.fromEntries(HANDLER_NAMES.map(name => [name, unimplementedHandler(name)])),
        sleep: () => sleep(riscv),
    })
    riscv.initialize(true)
    return riscv
}

const LOOP = [
    'main: li t0, 0',
    'loop: addi t0, t0, 1',
    '      li a7, 32',
    '      li a0, 5',
    '      ecall',
    'next: j loop',
]
const ECALL = TEXT + 16
const NEXT = TEXT + 20

// 1. A pause from the sleep handler ends the call after the ecall, with the count of what ran.
{
    const riscv = core(LOOP)
    assert.equal(riscv.getLastRunInstructionCount(), 0, 'nothing has run yet')
    assert.equal(await riscv.simulateWithLimit(1000), StopReason.PAUSE)
    assert.equal(riscv.getStopReason(), StopReason.PAUSE)
    assert.equal(riscv.getLastRunInstructionCount(), 5, 'li, addi, li, li and the ecall')
    assert.equal(riscv.programCounter, NEXT, 'the ecall has run, the instruction after it has not')
    assert.equal(riscv.terminated, false)
    assert.equal(riscv.getRegisterValue('t0'), 1)

    // the next call resumes there and runs one more iteration up to the next sleep
    assert.equal(await riscv.simulateWithLimit(1000), StopReason.PAUSE)
    assert.equal(riscv.getLastRunInstructionCount(), 5, 'j, addi, li, li and the ecall')
    assert.equal(riscv.getRegisterValue('t0'), 2)

    // each paused instruction is one Undo entry like any other
    riscv.undo()
    assert.equal(riscv.programCounter, ECALL, 'undo puts the program back on the ecall')
}

// 2. An async handler pauses once the promise settles and the instruction has finished.
{
    const riscv = core(LOOP, async (riscv) => {
        riscv.pause()
        await new Promise((resolve) => setTimeout(resolve, 5))
    })
    assert.equal(await riscv.simulateWithLimit(1000), StopReason.PAUSE)
    assert.equal(riscv.programCounter, NEXT)
}

// 3. A breakpoint on the instruction after the paused one wins: the call that resumes would run that
// instruction before checking anything, so a pause reported there would walk through the breakpoint.
{
    const riscv = core(LOOP)
    assert.equal(await riscv.simulateWithBreakpointsAndLimit([NEXT], 1000), StopReason.BREAKPOINT)
    assert.equal(riscv.programCounter, NEXT)
    assert.equal(riscv.getLastRunInstructionCount(), 5)
}

// 4. A pause after the program's last instruction is the end of the program, which that call says.
{
    const riscv = core(['main: li a7, 32', '      li a0, 1', '      ecall'])
    assert.equal(await riscv.simulateWithLimit(1000), StopReason.CLIFF_TERMINATION)
    assert.equal(riscv.terminated, true)
    assert.equal(riscv.getLastRunInstructionCount(), 3)
}

// 5. A pause is for the call in flight only: one asked for outside a call changes no later call, and
// a handler that does not pause leaves the call running to its limit.
{
    const riscv = core(LOOP, () => {})
    riscv.pause()
    assert.equal(await riscv.simulateWithLimit(12), StopReason.MAX_STEPS)
    assert.equal(riscv.getLastRunInstructionCount(), 12, 'the limit, exactly')
    assert.equal(await riscv.step(), StopReason.MAX_STEPS)
    assert.equal(riscv.getLastRunInstructionCount(), 1, 'a step is one instruction')
}

// 6. The count includes the instruction that exits or fails, is 0 for a call on an exited program,
// and initialize forgets it.
{
    const exit = core(['main: li t0, 1', '      li a7, 10', '      ecall', '      li t0, 2'])
    assert.equal(await exit.simulate(), StopReason.NORMAL_TERMINATION)
    assert.equal(exit.getLastRunInstructionCount(), 3, 'li, li and the exit')
    assert.equal(await exit.simulate(), StopReason.NORMAL_TERMINATION)
    assert.equal(exit.getLastRunInstructionCount(), 0, 'an exited program runs nothing')
    exit.initialize(true)
    assert.equal(exit.getLastRunInstructionCount(), 0)

    const failing = core(['main: li t0, 1', '      lw t1, 1(zero)'])
    await assert.rejects(failing.simulate(), (error) => isRuntimeError(error))
    assert.equal(failing.getLastRunInstructionCount(), 2, 'li and the failing lw')
}

console.log('pause: ok')
