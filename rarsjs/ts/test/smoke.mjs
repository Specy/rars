// Smoke test for the published artifact: assembles and runs a small RISC-V
// program through dist/, so it covers the whole Java -> TeaVM -> TypeScript
// chain rather than just type-checking. Run `npm run build` first.
import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const dist = new URL('../dist/index.mjs', import.meta.url)
if (!existsSync(fileURLToPath(dist))) {
    console.error('dist/index.mjs is missing - run `npm run build` (or `npm run build:all`) first.')
    process.exit(1)
}

const { RISCV, registerHandlers, unimplementedHandler, StopReason } = await import(dist)

const SOURCE = `
    .data
msg:    .string "sum = "

    .text
    .globl main
main:
    li   t0, 0              # accumulator
    li   t1, 1              # counter
    li   t2, 11             # loop bound
loop:
    add  t0, t0, t1
    addi t1, t1, 1
    blt  t1, t2, loop       # sum 1..10 == 55

    li   a7, 4              # print_string
    la   a0, msg
    ecall

    li   a7, 1              # print_int
    mv   a0, t0
    ecall

    li   a7, 10             # exit
    ecall
`

// Every handler must be registered; the ones this program cannot reach throw
// so an unexpected ecall fails the test instead of silently doing nothing.
const HANDLER_NAMES = [
    'openFile', 'closeFile', 'writeFile', 'readFile', 'confirm', 'inputDialog',
    'outputDialog', 'askDouble', 'askFloat', 'askInt', 'askString', 'readDouble',
    'readFloat', 'readInt', 'readString', 'readChar', 'logLine', 'log', 'printChar',
    'printDouble', 'printFloat', 'printInt', 'printString', 'sleep', 'stdIn', 'stdOut', 'stdErr',
]

// The simulator is a shared global, so the two width modes run in sequence.
for (const is64Bit of [false, true]) {
    RISCV.setIs64Bit(is64Bit)
    assert.equal(RISCV.is64Bit(), is64Bit, 'is64Bit should report the mode that was set')

    const output = []
    const riscv = RISCV.makeRiscVFromSource(SOURCE)

    registerHandlers(riscv, {
        ...Object.fromEntries(HANDLER_NAMES.map(name => [name, unimplementedHandler(name)])),
        printInt: value => output.push(String(value)),
        printString: value => output.push(value),
        printChar: value => output.push(value),
    })

    // No simulation has run yet, so there is no stop reason to report.
    assert.equal(riscv.getStopReason(), StopReason.NONE)

    const assembled = riscv.assemble()
    assert.equal(assembled.hasErrors, false, `assembly failed: ${JSON.stringify(assembled.errors)}`)

    riscv.initialize(true)

    const label = is64Bit ? 'RV64' : 'RV32'
    // step() resolves on a microtask, and settles later still if an IO handler
    // returned a promise, so the loop has to await each instruction.
    let steps = 0
    while (!riscv.terminated && steps < 10_000) {
        await riscv.step()
        steps++
    }

    assert.ok(riscv.terminated, `${label}: program did not terminate within ${steps} steps`)
    assert.equal(output.join(''), 'sum = 55', `${label}: unexpected program output`)
    assert.equal(riscv.getRegisterValue('t0'), 55, `${label}: wrong accumulator`)
    assert.equal(riscv.getRegisterValue('t1'), 11, `${label}: wrong counter`)
    assert.ok(riscv.getUndoStack().length > 0, `${label}: undo stack should record executed steps`)

    // The stop reason crosses from Java as an ordinal, so it must land on a
    // named member of StopReason rather than an opaque value.
    const reason = riscv.getStopReason()
    assert.ok(StopReason[reason] !== undefined, `${label}: unknown stop reason ${reason}`)

    console.log(`ok - ${label}: ran ${steps} instructions, printed "${output.join('')}", stopped on ${StopReason[reason]}`)
}

assert.ok(RISCV.getInstructionSet().length > 0, 'instruction set should not be empty')
