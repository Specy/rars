import assert from 'node:assert/strict'
import { RISCV, StopReason } from '../dist/index.mjs'

// Compiler-generated `call` expands to AUIPC/JALR, while handwritten code often uses JAL.
// Both must keep every recursive invocation, including repeated calls to the same address.
const expectedEntries = []
function visit(n, depth = 1) {
    expectedEntries.push({ n, depth })
    if (n > 1) {
        visit(n - 1, depth + 1)
        visit(n - 2, depth + 1)
    }
}
visit(10)

try {
    for (const is64Bit of [false, true]) {
        RISCV.setIs64Bit(is64Bit)
        const store = is64Bit ? 'sd' : 'sw'
        const load = is64Bit ? 'ld' : 'lw'
        for (const assemblerProfile of ['rars', 'gnu-compiler-v1']) {
            for (const call of [
                'jal ra, fibonacci_recursive',
                'call fibonacci_recursive',
                'la t0, fibonacci_recursive\n    jalr ra, t0, 0',
                'la t0, fibonacci_recursive\n    addi t0, t0, 1\n    jalr ra, t0, 0',
                'la ra, fibonacci_recursive\n    jalr ra, ra, 0',
            ]) {
                const core = RISCV.makeRiscVFromFiles({
                    'main.s': `.include "fibonacci.s"
.text
.globl main
main:
    li a0, 10
    ${call}
    li a7, 93
    ecall
`,
                    'fibonacci.s': `.text
.globl fibonacci_recursive
fibonacci_recursive:
    addi sp, sp, -32
    ${store} ra, 24(sp)
    sw a0, 16(sp)
    li t0, 1
    bgeu t0, a0, done
    addi a0, a0, -1
    call fibonacci_recursive
    sw a0, 8(sp)
    lw a0, 16(sp)
    addi a0, a0, -2
    call fibonacci_recursive
    lw t0, 8(sp)
    add a0, a0, t0
done:
    ${load} ra, 24(sp)
    addi sp, sp, 32
    ret
`,
                }, 'main.s', { assemblerProfile })
                const result = core.assemble()
                assert.equal(result.hasErrors, false, result.errors.map(error => error.message).join('\n'))
                core.initialize(true)
                const address = core.getAddressOfLabel('fibonacci_recursive')
                let entered = 0
                let reason
                for (let steps = 0; steps < 5000; steps++) {
                    reason = await core.step()
                    if (core.programCounter === address) {
                        const frames = Array.from(core.getCallStack())
                        const expected = expectedEntries[entered++]
                        assert.ok(expected, 'no extra recursive invocation')
                        assert.equal(Number(core.getRegistersValuesLong()[10]), expected.n)
                        assert.equal(frames.length, expected.depth, `fibonacci(${expected.n}) call depth`)
                        assert.ok(frames.every(frame => frame.toAddress === address))
                        assert.equal(frames.at(-1).pc, Number(core.getRegistersValuesLong()[1]), 'saved return address')
                        assert.equal(frames.at(-1).sp, Number(core.getRegistersValuesLong()[2]), 'stack pointer at the call')
                        assert.equal(core.getLabelAtAddress(address), 'fibonacci_recursive')
                    }
                    if (reason === StopReason.NORMAL_TERMINATION) break
                    assert.equal(reason, StopReason.MAX_STEPS)
                }
                assert.equal(reason, StopReason.NORMAL_TERMINATION)
                assert.equal(entered, expectedEntries.length, 'all 177 recursive invocations were traced')
                assert.equal(Number(core.getRegistersValuesLong()[10]), 55)
                assert.equal(core.getCallStack().length, 0, 'all returns removed their frames')
            }

            // An indirect jump without a link register is not a call. Check target bit clearing
            // as well, since a frame must point at the actual destination instruction.
            const jump = RISCV.makeRiscVFromFiles({
                'main.s': `.text
.globl main
main:
    la t0, destination
    addi t0, t0, 1
    jalr zero, t0, 0
destination:
    li a7, 10
    ecall
`,
            }, 'main.s', { assemblerProfile })
            assert.equal(jump.assemble().hasErrors, false)
            jump.initialize(true)
            for (let steps = 0; steps < 10 && !jump.terminated; steps++) {
                await jump.step()
                assert.equal(jump.getCallStack().length, 0, 'an indirect jump adds no frame')
            }
            assert.equal(jump.terminated, true)
            console.log(`ok - RV${is64Bit ? 64 : 32} ${assemblerProfile}: recursive direct and indirect calls, returns, and jumps`)
        }
    }
} finally {
    RISCV.setIs64Bit(false)
}
