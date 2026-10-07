import assert from 'node:assert/strict'
import { BackStepAction, RISCV, StopReason } from '../dist/index.mjs'

// The time counter is read from the time handler when an instruction reads it, so the read of time
// in the helper and of timeh in the loop (its upper half, on RV32; time again on RV64) each record
// their reading against themselves, as part of the instruction a single Undo reverts.
const source = (is64Bit) => `.text
.globl main
main:
    call caller
    li a7, 93
    ecall
helper:
    addi a0, a0, 1
    csrr t1, time
    jr ra
caller:
    addi sp, sp, -16
    sw ra, 12(sp)
    li a0, 5
    call helper
    li t0, 2
loop:
    addi t0, t0, -1
    ${is64Bit ? 'csrr t2, time' : 'csrr t2, timeh'}
    bgtz t0, loop
    lw ra, 12(sp)
    addi sp, sp, 16
    jr ra
`

// A clock the test controls, high enough that timeh is not zero.
let now = 0x1_8000_0000

try {
    for (const is64Bit of [false, true]) {
        RISCV.setIs64Bit(is64Bit)
        const core = RISCV.makeRiscVFromFiles({ 'main.asm': source(is64Bit) }, 'main.asm')
        core.registerHandler('time', () => now)
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
        const readings = { t1: [], t2: [] }
        let done = false
        while (!done && snapshots.length < 50) {
            const before = snapshot()
            snapshots.push(before)
            now += 2
            const reads = /csrr (t1|t2)/.exec(core.getNextStatement().source)?.[1]
            const reason = await core.step()
            done = reason === StopReason.NORMAL_TERMINATION
            const groups = core.getUndoGroups()
            assert.equal(groups.length, snapshots.length, 'one history group per executed instruction')
            assert.equal(groups[0].pc, before.pc, 'the newest group names the executed instruction')
            assert.equal(groups[0].steps.every(step => step.pc === before.pc), true,
                'every restore, including the time counter reading, belongs to that instruction')
            const reading = groups[0].steps.filter(step => step.action === BackStepAction.CONTROL_AND_STATUS_REGISTER_BACKDOOR)
            if (reads) {
                assert.equal(reading.length, 1, 'a read of the time counter records the reading it wrote')
                assert.equal(reading[0].param1, 0xC01, 'into the time counter, whichever half was read')
                assert.equal(BigInt(reading[0].newValue), BigInt(now), 'the time handler\'s clock')
                readings[reads].push(now)
            } else {
                assert.equal(reading.length, 0, 'nothing else writes the time counter')
            }
        }
        assert.ok(done, 'the program must complete its calls, returns, and branch loop')
        assert.deepEqual([readings.t1.length, readings.t2.length], [1, 2], 'one read in the helper and one per pass of the loop')
        const registers = core.getRegistersValuesLong()
        assert.equal(BigInt(registers[6]), BigInt(readings.t1[0]), 't1 holds the time the helper read')
        assert.equal(BigInt(registers[7]), is64Bit ? BigInt(readings.t2[1]) : BigInt(readings.t2[1]) >> 32n,
            't2 holds the time, or on RV32 its upper half, that the last pass read')
        for (const before of snapshots.reverse()) {
            core.undo()
            assert.deepEqual(snapshot(), before, 'one Undo restores the PC, source line, registers, and all CSRs')
        }
        assert.equal(core.canUndo, false)
        console.log(`ok - RV${is64Bit ? 64 : 32}: calls, returns, branches, and time counter reads undo together`)
    }
} finally {
    RISCV.setIs64Bit(false)
}
