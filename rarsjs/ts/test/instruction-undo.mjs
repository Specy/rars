import assert from 'node:assert/strict'
import { BackStepAction, RISCV, StopReason } from '../dist/index.mjs'

const source = `.text
.globl main
main:
    call caller
    li a7, 93
    ecall
helper:
    addi a0, a0, 1
    jr ra
caller:
    addi sp, sp, -16
    sw ra, 12(sp)
    li a0, 5
    call helper
    li t0, 2
loop:
    addi t0, t0, -1
    bgtz t0, loop
    lw ra, 12(sp)
    addi sp, sp, 16
    jr ra
`

// TeaVM samples new Date().getTime(), so advancing only Date.now() would miss the bug.
const RealDate = Date
let now = 1_700_000_000_000
globalThis.Date = class extends RealDate {
    constructor(...args) {
        super(...(args.length ? args : [now]))
    }
    static now() {
        return now
    }
}

try {
    for (const is64Bit of [false, true]) {
        RISCV.setIs64Bit(is64Bit)
        const core = RISCV.makeRiscVFromFiles({ 'main.asm': source }, 'main.asm')
        core.setUndoSize(200)
        assert.equal(core.assemble().hasErrors, false)
        core.setUndoEnabled(true)
        core.initialize(true)
        const snapshot = () => ({
            pc: core.programCounter,
            line: core.getNextStatement().sourceLine,
            registers: Array.from(core.getRegistersValuesLong()),
            csrs: Array.from(core.getControlAndStatusRegistersValues()),
        })
        const snapshots = []
        let sampledJump = false
        let done = false
        while (!done && snapshots.length < 50) {
            const before = snapshot()
            snapshots.push(before)
            now += 2
            const reason = await core.step()
            done = reason === StopReason.NORMAL_TERMINATION
            const groups = core.getUndoGroups()
            assert.equal(groups.length, snapshots.length, 'one history group per executed instruction')
            assert.equal(groups[0].pc, before.pc, 'the newest group names the executed instruction')
            assert.equal(groups[0].steps.every(step => step.pc === before.pc), true,
                'every restore, including the clock sample, belongs to that instruction')
            if (core.programCounter !== before.pc + 4 && !done) {
                assert.ok(groups[0].steps.some(step => step.action === BackStepAction.CONTROL_AND_STATUS_REGISTER_BACKDOOR),
                    'a clock sample after a jump must be covered')
                sampledJump = true
            }
        }
        assert.ok(done, 'the program must complete its calls, returns, and branch loop')
        assert.ok(sampledJump)
        for (const before of snapshots.reverse()) {
            core.undo()
            assert.deepEqual(snapshot(), before, 'one Undo restores the PC, source line, registers, and all CSRs')
        }
        assert.equal(core.canUndo, false)
        console.log(`ok - RV${is64Bit ? 64 : 32}: calls, returns, branches, and clock samples undo together`)
    }
} finally {
    globalThis.Date = RealDate
    RISCV.setIs64Bit(false)
}
