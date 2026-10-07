// How a program ends and how a run call says so: exit codes for exit, exit2 and running off the
// end, `terminated` read from the program's state (so it is right after Undo), a StopReason from
// every run call, and runtime failures rejected with a typed RuntimeError.
import assert from 'node:assert/strict'
import { RISCV, StopReason, isRuntimeError, registerHandlers, unimplementedHandler } from '../dist/index.mjs'

const HANDLER_NAMES = [
    'openFile', 'closeFile', 'writeFile', 'readFile', 'confirm', 'inputDialog',
    'outputDialog', 'readDouble', 'readFloat', 'readInt', 'readString', 'readChar',
    'printString', 'sleep', 'time', 'stdIn', 'seekFile', 'stdOut', 'stdErr', 'randomSeed',
]
const TEXT = 0x00400000

/** A core for the source lines, initialized; any handler not in `handlers` fails the run. */
function core(lines, handlers = {}) {
    const riscv = RISCV.makeRiscVFromFiles({ 'main.asm': lines.join('\n') }, 'main.asm')
    riscv.setUndoSize(1000)
    const result = riscv.assemble()
    assert.equal(result.hasErrors, false, result.report)
    registerHandlers(riscv, { ...Object.fromEntries(HANDLER_NAMES.map(name => [name, unimplementedHandler(name)])), ...handlers })
    riscv.initialize(true)
    return riscv
}

async function failure(run) {
    try {
        await run()
    } catch (error) {
        return error
    }
    assert.fail('the run should have failed')
}

for (const is64Bit of [false, true]) {
    RISCV.setIs64Bit(is64Bit)

    // 1. Exit codes. exit (10) gives 0, as does running off the end; exit2 (93) gives a0, signed.
    {
        const exit = core(['main: li t0, 1', '      li a7, 10', '      ecall', 'after: li t0, 2'])
        assert.equal(exit.getStopReason(), StopReason.NONE, 'nothing has run yet')
        assert.equal(exit.exitCode, 0)
        assert.equal(await exit.simulate(), StopReason.NORMAL_TERMINATION)
        assert.equal(exit.getStopReason(), StopReason.NORMAL_TERMINATION)
        assert.deepEqual([exit.exitCode, exit.terminated, exit.getNextStatement()], [0, true, null],
            'exit ends the program with code 0, and the statement after the ecall is not next')

        for (const code of [42, -1, 0x7fffffff]) {
            const exit2 = core(['main: li a0, ' + code, '      li a7, 93', '      ecall', '      li t0, 2'])
            const ecall = Array.from(exit2.getStatementsAtSourceLocation('main.asm', 3))[0].address
            assert.equal(await exit2.simulate(), StopReason.NORMAL_TERMINATION)
            assert.deepEqual([exit2.exitCode, exit2.terminated], [code, true], `exit2 with ${code}`)
            exit2.undo()
            assert.deepEqual([exit2.exitCode, exit2.terminated], [0, false], 'undoing exit2 puts back the code and the run')
            assert.equal(exit2.getNextStatement().address, ecall, 'back on the ecall')
            assert.equal(await exit2.step(), StopReason.NORMAL_TERMINATION, 'stepping it exits again')
            assert.equal(exit2.exitCode, code)
            exit2.initialize(true)
            assert.deepEqual([exit2.exitCode, exit2.terminated, exit2.getStopReason()], [0, false, StopReason.NONE],
                'initialize starts a run that has not exited')
        }

        // A later run that ends with exit reports 0, whatever an earlier run's exit2 set.
        const twice = core(['main: beqz s0, plain', '      li a0, 9', '      li a7, 93', '      ecall',
            'plain: li a7, 10', '      ecall'])
        twice.setRegisterValue('s0', 0, 1)
        await twice.simulate()
        assert.equal(twice.exitCode, 9)
        twice.initialize(true)
        await twice.simulate()
        assert.equal(twice.exitCode, 0)

        // Running off the end: the step that runs the last instruction says so, and `terminated`
        // is true at once, without a further step finding nothing to run.
        const cliff = core(['main: li t0, 1', '      li t1, 2'])
        assert.equal(await cliff.step(), StopReason.MAX_STEPS)
        assert.equal(cliff.terminated, false)
        assert.equal(await cliff.step(), StopReason.CLIFF_TERMINATION, 'the last instruction ran off the end')
        assert.deepEqual([cliff.terminated, cliff.getNextStatement(), cliff.exitCode], [true, null, 0])
        assert.equal(await cliff.step(), StopReason.CLIFF_TERMINATION, 'there is nothing more to run')
        assert.equal(cliff.getRegisterValue('t1'), 2)
        cliff.undo()
        assert.equal(cliff.terminated, false, 'undo leaves the program running again')
        assert.equal(cliff.getNextStatement().address, TEXT + 4)
        assert.equal(await cliff.simulateWithLimit(1), StopReason.CLIFF_TERMINATION, 'a limit reached at the end')
        cliff.initialize(true)
        assert.equal(await cliff.simulate(), StopReason.CLIFF_TERMINATION)
    }

    // 2. A StopReason from every run call, and nothing runs after an exit.
    {
        const lines = ['main: li t0, 1', '      li t0, 2', 'mark: li t0, 3', '      li t0, 4', '      li a7, 93',
            '      li a0, 5', 'last: ecall', '      li t0, 99']
        const riscv = core(lines)
        const mark = riscv.getAddressOfLabel('mark')
        const last = riscv.getAddressOfLabel('last')
        assert.equal(await riscv.step(), StopReason.MAX_STEPS)
        assert.equal(riscv.getStopReason(), StopReason.MAX_STEPS)
        assert.equal(await riscv.simulateWithBreakpoints([mark]), StopReason.BREAKPOINT)
        assert.equal(riscv.programCounter, mark, 'stopped before the breakpoint instruction')
        assert.equal(await riscv.simulateWithLimit(1), StopReason.MAX_STEPS)
        assert.equal(await riscv.simulateWithBreakpointsAndLimit([last], 1), StopReason.MAX_STEPS)
        assert.equal(await riscv.simulateWithBreakpointsAndLimit([last], 100), StopReason.BREAKPOINT)
        assert.equal(await riscv.simulate(), StopReason.NORMAL_TERMINATION)
        assert.deepEqual([riscv.exitCode, riscv.getRegisterValue('t0')], [5, 4])
        const depth = riscv.getUndoDepth()
        for (const run of [() => riscv.step(), () => riscv.simulate(), () => riscv.simulateWithLimit(10),
            () => riscv.simulateWithBreakpoints([TEXT]), () => riscv.simulateWithBreakpointsAndLimit([TEXT], 10)]) {
            assert.equal(await run(), StopReason.NORMAL_TERMINATION, 'a program that exited stays exited')
        }
        assert.deepEqual([riscv.getRegisterValue('t0'), riscv.getUndoDepth()], [4, depth], 'and nothing after the ecall ran')

        // ebreak stops a run as a breakpoint does, and the program goes on from there.
        const ebreak = core(['main: li t0, 1', '      ebreak', '      li t0, 2'])
        assert.equal(await ebreak.simulate(), StopReason.BREAKPOINT)
        assert.equal(await ebreak.simulate(), StopReason.CLIFF_TERMINATION)
        assert.equal(ebreak.getRegisterValue('t0'), 2)
    }

    // 3. Runtime failures reject with a RuntimeError: kind, address, source location, RARS's message.
    {
        const exception = core(['main: li t1, 1', '      lw t0, 3(zero)', '      li a7, 10', '      ecall'])
        await exception.step()
        const error = await failure(() => exception.step())
        assert.ok(error instanceof Error && isRuntimeError(error), String(error))
        assert.equal(error.name, 'RuntimeError')
        assert.deepEqual([error.kind, error.address, error.sourcePath, error.line], ['exception', TEXT + 4, 'main.asm', 2])
        assert.match(error.message, /^Runtime exception at 0x00400004: /)
        assert.match(String(error), /^RuntimeError: Runtime exception at 0x00400004/)
        assert.equal(exception.getStopReason(), StopReason.EXCEPTION)
        assert.equal(exception.terminated, false, 'a failure is reported by the rejection, not by terminated')

        const unknown = core(['main: li a7, 99', '      ecall'])
        const unknownError = await failure(() => unknown.simulate())
        assert.deepEqual([unknownError.kind, unknownError.address, unknownError.line, unknownError.message],
            ['syscall', TEXT + 4, 2, 'Runtime exception at 0x00400004: invalid or unimplemented syscall service: 99'])

        const input = core(['main: li a7, 5', '      ecall'], { readInt: () => 'abc' })
        const inputError = await failure(() => input.simulate())
        assert.deepEqual([inputError.kind, inputError.message], ['syscall', 'Runtime exception at 0x00400004: invalid integer input (syscall 5)'])

        const contract = core(['main: li a7, 5', '      ecall'], { readInt: () => 5 })
        const contractError = await failure(() => contract.simulate())
        assert.deepEqual([contractError.kind, contractError.address, contractError.line, contractError.message],
            ['handler', TEXT + 4, 2, 'Handler readInt did not return a string'])
        assert.equal(contractError.cause, undefined)

        const boom = new Error('boom')
        const thrown = core(['main: li a7, 5', '      ecall'], { readInt: () => { throw boom } })
        const thrownError = await failure(() => thrown.simulate())
        assert.deepEqual([thrownError.kind, thrownError.message], ['handler', 'Handler readInt threw: boom'])
        assert.equal(thrownError.cause, boom, 'the cause is what the handler threw')

        const nope = new Error('nope')
        const rejected = core(['main: li a7, 5', '      ecall'], { readInt: () => Promise.reject(nope) })
        const rejectedError = await failure(() => rejected.simulate())
        assert.deepEqual([rejectedError.kind, rejectedError.message], ['handler', 'Handler readInt rejected: nope'])
        assert.equal(rejectedError.cause, nope, 'the cause is what the promise rejected with')

        // A memory observer is host code too.
        const observed = core(['main: li t0, 0x10010000', 'store: sw t0, 0(t0)', '      li a7, 10', '      ecall'])
        const handle = observed.addMemoryWriteObserver(0x10010000, 0x10010000, () => { throw new Error('observer') })
        const observerError = await failure(() => observed.simulate())
        observed.removeMemoryObserver(handle)
        assert.deepEqual([observerError.kind, observerError.address, observerError.line, observerError.message],
            ['handler', observed.getAddressOfLabel('store'), 2, 'Memory observer threw: observer'])

        // A jump out of the program fails on the fetch, which has no statement.
        const lost = core(['main: jr zero'])
        assert.equal(await lost.step(), StopReason.MAX_STEPS)
        const lostError = await failure(() => lost.step())
        assert.deepEqual([lostError.kind, lostError.address, lostError.sourcePath, lostError.line],
            ['exception', 0, null, null])
        assert.equal(lostError.message, 'Instruction load access error')

        // Calling a program that never assembled is not a runtime failure.
        const broken = RISCV.makeRiscVFromFiles({ 'main.asm': 'main: bogus' }, 'main.asm')
        broken.assemble()
        const misuse = await failure(() => broken.step())
        assert.equal(isRuntimeError(misuse), false)
        assert.match(String(misuse.message), /not been assembled successfully/)
    }
    console.log(`ok - ${is64Bit ? 'RV64' : 'RV32'} termination: exit codes, terminated after Undo, a StopReason from every run call, typed runtime errors`)
}
RISCV.setIs64Bit(false)
