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

const packageExports = await import(dist)
const {
    RISCV, makeRiscVFromFiles, registerHandlers, unimplementedHandler, StopReason,
    RISCV_FLOATING_POINT_REGISTERS, RISCV_CSR_REGISTERS, bigintToHighLow, highLowToBigint,
    BackStepAction, RISCV_REGISTERS,
} = packageExports

const makeSingleFileRiscV = source => makeRiscVFromFiles({ 'main.asm': source }, 'main.asm')

assert.equal('makeRiscVFromSource' in packageExports, false, 'the v2 single-source export must be removed')
assert.equal('makeRiscVFromSource' in RISCV, false, 'the v2 single-source static factory must be removed')

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

const WARNINGS_ONLY_SOURCE = `
    .data
val: .byte 300
    .text
main:
    li a7, 10
    ecall
`

const REAL_ERROR_SOURCE = `
    .text
main:
    bogus_instruction
`

// Every handler must be registered; the ones this program cannot reach throw
// so an unexpected ecall fails the test instead of silently doing nothing.
const HANDLER_NAMES = [
    'openFile', 'closeFile', 'writeFile', 'readFile', 'confirm', 'inputDialog',
    'outputDialog', 'askDouble', 'askFloat', 'askInt', 'askString', 'readDouble',
    'readFloat', 'readInt', 'readString', 'readChar', 'logLine', 'log', 'printChar',
    'printDouble', 'printFloat', 'printInt', 'printString', 'sleep', 'time', 'stdIn', 'stdOut',
    'stdErr',
]

RISCV.setIs64Bit(false)

const warningProgram = makeSingleFileRiscV(WARNINGS_ONLY_SOURCE)
const warningsOnly = warningProgram.assemble()
assert.equal(warningsOnly.hasErrors, false, `warnings-only assembly failed: ${warningsOnly.report}`)
assert.equal(warningsOnly.hasWarnings, true, 'warnings-only assembly should report warnings')
assert.ok(warningsOnly.errors.length >= 1, 'warnings-only assembly should include at least one diagnostic')
assert.equal(warningsOnly.errors.every(error => error.isWarning === true), true, 'every warnings-only diagnostic should expose isWarning: true')

warningProgram.initialize(true)
let warningSteps = 0
while (!warningProgram.terminated && warningSteps < 10) {
    await warningProgram.step()
    warningSteps++
}
assert.ok(warningProgram.terminated, 'warnings-only program should remain runnable')

const realErrorProgram = makeSingleFileRiscV(REAL_ERROR_SOURCE)
const realError = realErrorProgram.assemble()
assert.equal(realError.hasErrors, true, 'invalid assembly should report an error')
assert.ok(realError.errors.some(error => error.isWarning === false), 'invalid assembly should expose isWarning: false')
assert.ok(realErrorProgram.getTokenizedLines().length > 0, 'tokens should remain available after completed tokenization')
assert.throws(() => realErrorProgram.getCompiledStatements(), /not been assembled successfully/)
assert.throws(() => realErrorProgram.initialize(true), /not been assembled successfully/)

// The simulator is a shared global, so the two width modes run in sequence.
for (const is64Bit of [false, true]) {
    RISCV.setIs64Bit(is64Bit)
    assert.equal(RISCV.is64Bit(), is64Bit, 'is64Bit should report the mode that was set')

    const output = []
    const riscv = makeSingleFileRiscV(SOURCE)

    registerHandlers(riscv, {
        ...Object.fromEntries(HANDLER_NAMES.map(name => [name, unimplementedHandler(name)])),
        printInt: value => output.push(String(value)),
        printString: value => output.push(value),
        printChar: value => output.push(value),
    })

    // No simulation has run yet, so there is no stop reason to report.
    assert.equal(riscv.getStopReason(), StopReason.NONE)

    const assembled = riscv.assemble()
    assert.equal(assembled.hasErrors, false, `assembly failed: ${assembled.report}`)
    assert.equal(assembled.hasWarnings, false, `clean assembly produced warnings: ${assembled.report}`)

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

// `slli`, `srli` and `srai` name a different operation on each width. On RV64 they shift all 64
// bits and take a 6 bit shift amount; the 32 bit forms of the same mnemonics, which shift the low
// half and sign extend, are `slliw`, `srliw` and `sraiw`. Registering both sets on RV64 let the
// 32 bit one match every shift amount below 32, so `slli t1, t0, 31` returned the `slliw` answer
// and `li` of any constant wider than 32 bits truncated, since `li` expands to these shifts.
const shiftProgram = `
    .text
    .globl main
main:
    li   t0, 1
    slli t1, t0, 31         # all 64 bits: 0x0000000080000000
    slliw t2, t0, 31        # the 32 bit form, sign extended: 0xFFFFFFFF80000000
    li   t3, 0x1122334455667788
    srli t4, t3, 8          # 0x0011223344556677
    li   t5, -256
    srai t6, t5, 4          # 0xFFFFFFFFFFFFFFF0
    li   a7, 10
    ecall
`

RISCV.setIs64Bit(true)
const shifts = makeSingleFileRiscV(shiftProgram)
registerHandlers(shifts, Object.fromEntries(HANDLER_NAMES.map(name => [name, unimplementedHandler(name)])))
const shiftAssembled = shifts.assemble()
assert.equal(shiftAssembled.hasErrors, false, `shift program failed to assemble: ${shiftAssembled.report}`)
shifts.initialize(true)
{
    let steps = 0
    while (!shifts.terminated && steps < 10_000) {
        await shifts.step()
        steps++
    }
    assert.ok(shifts.terminated, 'shift program did not terminate')
}
// The single-register accessor hands back a raw Java BigInteger that BigInt() cannot read; the
// array form yields values it can, which is how the editor reads the 64 bit file too.
const shiftRegisters = Array.from(shifts.getRegistersValuesLong(), value => BigInt(value))
const shiftValue = name =>
    BigInt.asUintN(64, shiftRegisters[packageExports.RISCV_REGISTERS.indexOf(name)])
assert.equal(shiftValue('t1'), 0x0000000080000000n, 'RV64 slli must shift all 64 bits')
assert.equal(shiftValue('t2'), 0xFFFFFFFF80000000n, 'slliw must shift 32 bits and sign extend')
assert.equal(shiftValue('t3'), 0x1122334455667788n, 'li must keep a constant wider than 32 bits')
assert.equal(shiftValue('t4'), 0x0011223344556677n, 'RV64 srli must shift all 64 bits')
assert.equal(shiftValue('t6'), 0xFFFFFFFFFFFFFFF0n, 'RV64 srai must shift all 64 bits')
RISCV.setIs64Bit(false)
console.log('ok - RV64 immediate shifts operate on all 64 bits')

assert.ok(RISCV.getInstructionSet().length > 0, 'instruction set should not be empty')

// Multi-file construction: one immutable virtual source tree, rooted at the entry file.
RISCV.setIs64Bit(false)
const projectFiles = {
    'src/main.asm': [
        '.include "../shared/macros.asm"',
        '.eqv EXIT_CODE 10',
        '.text',
        '.globl main',
        'main:',
        '    load_magic(t0)',
        '    jal helper',
        '    li a7, EXIT_CODE',
        '    ecall',
        '.include "./helper.asm"',
        '.include "/shared/padding.asm"',
        '.include "../shared/padding.asm"',
    ].join('\n'),
    'src/helper.asm': [
        '.text',
        'helper:',
        '    addi t0, t0, 1',
        '    ret',
    ].join('\n'),
    'shared/macros.asm': [
        '.macro load_magic(%register)',
        '    li %register, 0x12345678',
        '.end_macro',
    ].join('\n'),
    'shared/padding.asm': [
        '.text',
        '    nop',
    ].join('\n'),
    'unused.asm': 'bogus_instruction',
}

const project = RISCV.makeRiscVFromFiles(projectFiles, 'src/main.asm')
assert.throws(() => project.getTokenizedLines(), /not been assembled/, 'tokens should be unavailable before assembly')

// Construction snapshots the caller's object; edits after this point cannot affect the program.
projectFiles['src/main.asm'] = 'bogus_instruction'
projectFiles['new.asm'] = 'bogus_instruction'

const projectAssembly = project.assemble()
assert.equal(projectAssembly.hasErrors, false, `multi-file assembly failed: ${projectAssembly.report}`)

const tokenizedLines = Array.from(project.getTokenizedLines())
assert.equal(tokenizedLines.some(line => line.source.includes('.include')), false, 'include directives should be replaced')
assert.equal(tokenizedLines.some(line => line.sourcePath === 'unused.asm'), false, 'unused source files should be ignored')

const substitutedLine = tokenizedLines.find(line => line.sourcePath === 'src/main.asm' && line.sourceLine === 8)
assert.equal(substitutedLine.source.trim(), 'li a7, EXIT_CODE')
assert.equal(substitutedLine.processedSource.trim(), 'li a7, 10')
assert.ok(substitutedLine.tokens.every(token => !('sourceLine' in token) && !('originalSourceLine' in token)))
assert.ok(substitutedLine.tokens.every(token => Number.isInteger(token.sourceColumn) && token.sourceColumn >= 1))

const macroStatements = Array.from(project.getStatementsAtSourceLocation('src/main.asm', 6))
assert.ok(macroStatements.length >= 2, 'the large li in the macro should expand to multiple machine statements')
assert.ok(macroStatements.every(statement => statement.sourcePath === 'src/main.asm'))
assert.ok(macroStatements.every(statement => statement.sourceLine === 6))
assert.ok(macroStatements.every(statement => statement.source.trim() === 'load_magic(t0)'))
assert.deepEqual(
    macroStatements.map(statement => statement.address),
    [...macroStatements].map(statement => statement.address).sort((a, b) => a - b),
    'source lookup should return machine statements in address order',
)

const repeatedStatements = Array.from(project.getStatementsAtSourceLocation('shared/padding.asm', 2))
assert.equal(repeatedStatements.length, 2, 'each textual inclusion should produce its own machine statement')
assert.deepEqual(Array.from(project.getStatementsAtSourceLocation('missing.asm', 1)), [])
assert.throws(() => project.getStatementsAtSourceLocation('./src/main.asm', 1), /canonical|root-relative/)
assert.throws(() => project.getStatementsAtSourceLocation('src/main.asm', 0), /positive integer/)
assert.throws(() => project.getStatementsAtSourceLocation('src/main.asm', 1.5), /positive integer/)
assert.ok(project.getCompiledStatements().length > macroStatements.length, 'the complete machine program should remain available')
assert.ok(project.getParsedStatements().every(statement => typeof statement.sourcePath === 'string'))

project.initialize(true)
assert.equal(project.getNextStatement().sourcePath, 'src/main.asm')

// A call stack frame names the label it jumped to, wherever that label was declared. A .globl
// label - which is how one file calls into another - is moved out of the local symbol table and
// into the global one at assembly, so a local-only lookup would miss it.
const callStackProgram = makeRiscVFromFiles({
    'main.asm': [
        '.include "library.asm"',
        '.text',
        '.globl main',
        'main:',
        '    jal ra, shared',
        '    jal ra, private',
        '    li a7, 10',
        '    ecall',
        'private:',
        '    jr ra',
    ].join('\n'),
    'library.asm': [
        '.text',
        '.globl shared',
        'shared:',
        '    jr ra',
    ].join('\n'),
}, 'main.asm')
assert.equal(callStackProgram.assemble().hasErrors, false)
callStackProgram.initialize(true)

const visitedFrameLabels = []
let callStackSteps = 0
while (!callStackProgram.terminated && callStackSteps < 100) {
    await callStackProgram.step()
    callStackSteps++
    for (const frame of callStackProgram.getCallStack()) {
        const label = callStackProgram.getLabelAtAddress(frame.toAddress)
        if (!visitedFrameLabels.includes(label)) visitedFrameLabels.push(label)
    }
}
assert.deepEqual(visitedFrameLabels, ['shared', 'private'], 'both global and local callees should resolve to a name')
assert.equal(callStackProgram.getLabelAtAddress(0x12345678), null, 'an address with no label is not an error')

assert.throws(
    () => makeRiscVFromFiles({ './main.asm': SOURCE }, './main.asm'),
    /canonical|root-relative/,
    'noncanonical source keys should fail construction',
)
assert.throws(
    () => makeRiscVFromFiles({ 'main.asm': SOURCE }, 'missing.asm'),
    /not present/,
    'the entry file must exist',
)

const opaquePathAssembly = makeRiscVFromFiles({
    'directory with spaces/π.library.asm': '.text\nnop',
}, 'directory with spaces/π.library.asm').assemble()
assert.equal(opaquePathAssembly.hasErrors, false, 'valid source path segments should remain opaque')

const missingIncludeProgram = makeRiscVFromFiles({
    'main.asm': '.include "missing.asm"',
}, 'main.asm')
const missingInclude = missingIncludeProgram.assemble()
assert.equal(missingInclude.hasErrors, true)
assert.equal(missingInclude.errors[0].sourcePath, 'main.asm')
assert.equal(missingInclude.errors[0].sourceLine, 1)
assert.ok(missingInclude.errors[0].sourceColumn >= 1)
assert.equal('filename' in missingInclude.errors[0], false)
assert.throws(() => missingIncludeProgram.getTokenizedLines(), /tokenization did not complete/)

const escapingInclude = makeRiscVFromFiles({
    'main.asm': '.include "../outside.asm"',
}, 'main.asm').assemble()
assert.equal(escapingInclude.hasErrors, true)
assert.match(escapingInclude.report, /escapes the virtual root/)

const includeCycle = makeRiscVFromFiles({
    'entry.asm': '.include "a.asm"',
    'a.asm': '.include "dir/b.asm"',
    'dir/b.asm': '.include "../a.asm"',
}, 'entry.asm').assemble()
assert.equal(includeCycle.hasErrors, true)
assert.match(includeCycle.report, /entry\.asm -> a\.asm -> dir\/b\.asm -> a\.asm/)
assert.equal(includeCycle.errors[0].sourcePath, 'dir/b.asm')

const macroError = makeRiscVFromFiles({
    'main.asm': [
        '.include "macros.asm"',
        '.text',
        '.globl main',
        'main:',
        '    bad()',
    ].join('\n'),
    'macros.asm': [
        '.macro bad()',
        '    bogus_instruction',
        '.end_macro',
    ].join('\n'),
}, 'main.asm').assemble()
assert.equal(macroError.hasErrors, true)
const expandedDiagnostic = macroError.errors.find(error => error.macroExpansionTrace.length > 0)
assert.ok(expandedDiagnostic, 'macro diagnostics should expose a structured expansion trace')
assert.equal(expandedDiagnostic.sourcePath, 'macros.asm')
assert.deepEqual(Array.from(expandedDiagnostic.macroExpansionTrace).map(location => ({
    sourcePath: location.sourcePath,
    sourceLine: location.sourceLine,
})), [{ sourcePath: 'main.asm', sourceLine: 5 }])

// Peripherals: the framebuffer range, a memory-mapped register and program time. The program
// stores three words into static data, reads the register word back, sleeps and asks for the time.
const FRAMEBUFFER = 0x10010000
// Deliberately the unsigned form: a memory-mapped address does not fit a positive int, and the
// wrapper has to accept it anyway or an observer registered from it could never match an access.
const REGISTER = 0xffff0000
// What the guest holds, and so what an observer is handed.
const SIGNED_REGISTER = 0xffff0000 | 0

const PERIPHERAL_SOURCE = `
    .text
    .globl main
main:
    li   t0, 0x10010000
    li   t1, 0x00ff0012
    sw   t1, 0(t0)
    sw   t1, 4(t0)
    sb   t1, 8(t0)          # a byte store must report length 1

    li   t2, 0xffff0000
    lw   t3, 0(t2)          # read of the observed register
    li   t4, 7
    sw   t4, 0(t2)          # write of the observed register

    li   a7, 32             # sleep
    li   a0, 25
    ecall

    li   a7, 30             # time
    ecall
    mv   s0, a0

    li   a7, 10
    ecall
`

// The register lives in the memory map, which the simulator only exposes in 32 bit mode.
RISCV.setIs64Bit(false)

const writes = []
const registerReads = []
const registerWrites = []
const slept = []
let clock = 1000

const peripherals = makeSingleFileRiscV(PERIPHERAL_SOURCE)
registerHandlers(peripherals, {
    ...Object.fromEntries(HANDLER_NAMES.map(name => [name, unimplementedHandler(name)])),
    // A virtual clock: sleeping advances it instead of waiting, the scripted-run shape.
    sleep: milliseconds => {
        slept.push(milliseconds)
        clock += milliseconds
    },
    time: () => clock,
})

// Registering before assembling is allowed; notifications start once a program is assembled.
const framebufferHandle = peripherals.addMemoryWriteObserver(
    FRAMEBUFFER,
    FRAMEBUFFER + 8,
    (address, length, value) => writes.push([address, length, value])
)
const registerHandle = peripherals.addMemoryAccessObserver(
    REGISTER,
    (address, value) => registerReads.push([address, value]),
    (address, value) => registerWrites.push([address, value])
)
assert.equal(peripherals.countMemoryObservers(), 2)

const peripheralAssembly = peripherals.assemble()
assert.equal(peripheralAssembly.hasErrors, false, `peripheral assembly failed: ${peripheralAssembly.report}`)

// A peripheral preloads its register the way a keyboard would; that write must stay invisible.
peripherals.setPeripheralWord(REGISTER, 0x41)
assert.equal(registerWrites.length, 0, 'setPeripheralWord must not notify the observer')

peripherals.initialize(true)
let peripheralSteps = 0
while (!peripherals.terminated && peripheralSteps < 10_000) {
    await peripherals.step()
    peripheralSteps++
}
assert.ok(peripherals.terminated, 'peripheral program did not terminate')

assert.deepEqual(writes, [
    [FRAMEBUFFER, 4, 0x00ff0012],
    [FRAMEBUFFER + 4, 4, 0x00ff0012],
    // A byte store reports only the byte it wrote, not the whole register.
    [FRAMEBUFFER + 8, 1, 0x12],
], 'framebuffer writes should be reported in order, with the width of each store')
assert.deepEqual(registerReads, [[SIGNED_REGISTER, 0x41]], 'the register read should report the preloaded value')
assert.deepEqual(registerWrites, [[SIGNED_REGISTER, 7]], 'the register write should report the stored value')

// readMemoryBytes is host inspection, so it must not look like a program read.
assert.deepEqual(Array.from(peripherals.readMemoryBytes(REGISTER, 1)), [7])
assert.equal(registerReads.length, 1, 'readMemoryBytes must not notify a read observer')

assert.deepEqual(slept, [25], 'syscall 32 should reach the sleep handler with a0')
assert.equal(peripherals.getRegisterValue('s0'), 1025, 'syscall 30 should report the handler clock')

// Undo replays the memory restores through the same stores, so observers hear them: an adapter
// that follows the notifications alone still ends up with the rolled-back image.
const writesBeforeUndo = writes.length
while (peripherals.canUndo) {
    peripherals.undo()
}
assert.deepEqual(writes.slice(writesBeforeUndo), [
    [FRAMEBUFFER + 8, 1, 0],
    [FRAMEBUFFER + 4, 4, 0],
    [FRAMEBUFFER, 4, 0],
], 'undo should report each restored word, newest first, as an ordinary write')

// Assembling clears memory but not the registrations, so a rebuilt program is still observed.
// `terminated` reports the last stop reason and initialize() does not clear it, so the second run
// is driven by simulateWithLimit rather than by a loop over `terminated`.
const writesBeforeRebuild = writes.length
const reassembled = peripherals.assemble()
assert.equal(reassembled.hasErrors, false, `reassembly failed: ${reassembled.report}`)
peripherals.setPeripheralWord(REGISTER, 0x42)
peripherals.initialize(true)
// The exit syscall stops the run itself, so this is NORMAL_TERMINATION; the loop above only ever
// saw CLIFF_TERMINATION because `terminated` ignores every other reason and let it step off the end.
const rebuiltReason = await peripherals.simulateWithLimit(10_000)
assert.equal(rebuiltReason, StopReason.NORMAL_TERMINATION, 'the rebuilt program should run to its exit')
assert.deepEqual(writes.slice(writesBeforeRebuild, writesBeforeRebuild + 3), [
    [FRAMEBUFFER, 4, 0x00ff0012],
    [FRAMEBUFFER + 4, 4, 0x00ff0012],
    [FRAMEBUFFER + 8, 1, 0x12],
], 'observers should survive assemble() and initialize()')

peripherals.removeMemoryObserver(framebufferHandle)
assert.equal(peripherals.countMemoryObservers(), 1)
peripherals.removeMemoryObserver(registerHandle)
assert.equal(peripherals.countMemoryObservers(), 0)

// Observers are shared by every instance, so a leftover registration would fire for the next
// program; removing them all is what a fresh build should do.
peripherals.removeMemoryObservers()

console.log(`ok - peripherals: ${writes.length} observed writes, slept ${slept.join(',')}ms, clock ${clock}`)

// The floating point and control and status register files. Both cross the boundary as flat
// arrays of high/low int pairs, so every assertion here goes through `highLowToBigint`.
const FP_SOURCE = `
    .data
buf:    .word 0

    .text
    .globl main
main:
    li   t0, 3
    fcvt.s.w ft0, t0        # 3.0f, NaN-boxed into the 64 bit register
    fcvt.d.s ft1, ft0       # 3.0d
    fadd.s   ft2, ft0, ft0  # 6.0f
    la   t1, buf
    fsw  ft2, 0(t1)
    flw  ft3, 0(t1)         # 6.0f, round-tripped through memory
    li   a7, 10
    ecall
`

const NAN_BOXED_3F = 0xFFFFFFFF40400000n
const NAN_BOXED_6F = 0xFFFFFFFF40C00000n
const DOUBLE_3 = 0x4008000000000000n

const readRegisterFile = halves => {
    const flat = Array.from(halves)
    assert.equal(flat.length % 2, 0, 'a register file is returned as high/low pairs')
    const values = []
    for (let i = 0; i < flat.length; i += 2) values.push(highLowToBigint(flat[i], flat[i + 1]))
    return values
}

const runFpProgram = async () => {
    const program = makeSingleFileRiscV(FP_SOURCE)
    registerHandlers(program, Object.fromEntries(HANDLER_NAMES.map(name => [name, unimplementedHandler(name)])))
    const assembled = program.assemble()
    assert.equal(assembled.hasErrors, false, `floating point assembly failed: ${assembled.report}`)
    program.initialize(true)
    let steps = 0
    while (!program.terminated && steps < 10_000) {
        await program.step()
        steps++
    }
    assert.ok(program.terminated, 'floating point program did not terminate')
    return program
}

assert.equal(RISCV_FLOATING_POINT_REGISTERS.length, 32)
assert.equal(RISCV_FLOATING_POINT_REGISTERS[0], 'ft0')
assert.equal(RISCV_FLOATING_POINT_REGISTERS[8], 'fs0')
assert.equal(RISCV_FLOATING_POINT_REGISTERS[10], 'fa0')
assert.deepEqual(RISCV_CSR_REGISTERS.slice(0, 4), ['ustatus', 'fflags', 'frm', 'fcsr'])
assert.equal(RISCV_CSR_REGISTERS.length, 17)
assert.equal(RISCV_CSR_REGISTERS[13], 'instret')

assert.equal(highLowToBigint(0xFFFFFFFF | 0, 0x40400000), NAN_BOXED_3F, 'both halves compose unsigned')
assert.deepEqual(bigintToHighLow(NAN_BOXED_3F), [0xFFFFFFFF, 0x40400000])

RISCV.setIs64Bit(false)
const fpu = await runFpProgram()

const fpRaw = Array.from(fpu.getFloatingPointRegistersValues())
assert.equal(fpRaw.length, 64, 'the floating point file is 32 registers of two halves')
assert.equal(fpRaw[0] >>> 0, 0xFFFFFFFF, 'ft0 high half: a single is NaN-boxed')
assert.equal(fpRaw[1] >>> 0, 0x40400000, 'ft0 low half: 3.0f')

const fp = readRegisterFile(fpu.getFloatingPointRegistersValues())
assert.equal(fp[0], NAN_BOXED_3F, 'ft0 should hold 3.0f, NaN-boxed')
assert.equal(fp[1], DOUBLE_3, 'ft1 should hold 3.0d')
assert.equal(fp[2], NAN_BOXED_6F, 'ft2 should hold 6.0f')
assert.equal(fp[3], NAN_BOXED_6F, 'ft3 should hold 6.0f read back from memory')
assert.ok(fp.slice(4).every(value => value === 0n), 'untouched floating point registers stay zero')

const csr = readRegisterFile(fpu.getControlAndStatusRegistersValues())
assert.equal(csr.length, RISCV_CSR_REGISTERS.length, 'the control and status file is 17 registers')
const csrByName = name => csr[RISCV_CSR_REGISTERS.indexOf(name)]
assert.ok(csrByName('instret') > 0n, 'instret should count the instructions executed')
assert.equal(csrByName('cycle'), csrByName('instret'), 'cycle and instret advance together')
assert.equal(csrByName('cycleh'), csrByName('cycle') >> 32n, 'a linked register reads through its base')
assert.equal(csrByName('timeh'), csrByName('time') >> 32n, 'the time halves agree')

// Both counters are zero in their high half after a ten instruction program, so the equalities
// above only pin the zero state: give a counter a non-zero high half through the setter and check
// that the *h half really reads through its base, which is how RV32 presents a 64 bit counter.
fpu.setControlAndStatusRegisterValue(RISCV_CSR_REGISTERS.indexOf('cycle'), 0x2A, 7)
const csrLinked = readRegisterFile(fpu.getControlAndStatusRegistersValues())
const csrLinkedByName = name => csrLinked[RISCV_CSR_REGISTERS.indexOf(name)]
assert.equal(csrLinkedByName('cycle'), 0x2A00000007n, 'the counter should hold both halves written')
assert.equal(csrLinkedByName('cycleh'), 0x2An, 'cycleh should read the high half of cycle')

// Setters write the register directly: presetting one from the host is not something the program
// did, so it must not land in the undo history.
const undoStackBefore = fpu.getUndoStack().length
assert.ok(undoStackBefore > 0, 'the executed program should have recorded undo entries')

const PRESET_FP = 0x123456789ABCDEF0n
fpu.setFloatingPointRegisterValue(5, ...bigintToHighLow(PRESET_FP))
fpu.setControlAndStatusRegisterValue(RISCV_CSR_REGISTERS.indexOf('uscratch'), 0, 0x77)
// A linked register writes the register it aliases.
fpu.setControlAndStatusRegisterValue(RISCV_CSR_REGISTERS.indexOf('frm'), 0, 3)

const fpAfterSet = readRegisterFile(fpu.getFloatingPointRegistersValues())
assert.equal(fpAfterSet[5], PRESET_FP, 'the floating point setter should round-trip through the getter')
const csrAfterSet = readRegisterFile(fpu.getControlAndStatusRegistersValues())
const csrAfterSetByName = name => csrAfterSet[RISCV_CSR_REGISTERS.indexOf(name)]
assert.equal(csrAfterSetByName('uscratch'), 0x77n, 'the control and status setter should round-trip')
assert.equal(csrAfterSetByName('frm'), 3n, 'writing frm should read back')
assert.equal(csrAfterSetByName('fcsr'), 0x60n, 'writing frm should update the fcsr it aliases')
assert.equal(fpu.getUndoStack().length, undoStackBefore, 'a setter must not add an undo entry')

// The core rolls its own floating point writes back, so undo restores the file without the
// wrapper touching it - while the values preset above, which were never undo entries, survive.
assert.ok(fpu.canUndo, 'the program should still be undoable')
while (fpu.canUndo) fpu.undo()
const fpAfterUndo = readRegisterFile(fpu.getFloatingPointRegistersValues())
assert.equal(fpAfterUndo[0], 0n, 'undo should restore ft0')
assert.equal(fpAfterUndo[1], 0n, 'undo should restore ft1')
assert.equal(fpAfterUndo[2], 0n, 'undo should restore ft2')
assert.equal(fpAfterUndo[3], 0n, 'undo should restore ft3')
assert.equal(fpAfterUndo[5], PRESET_FP, 'undo must not roll back a value the host preset')
const csrAfterUndo = readRegisterFile(fpu.getControlAndStatusRegistersValues())
assert.equal(csrAfterUndo[RISCV_CSR_REGISTERS.indexOf('instret')], 0n, 'undo should roll the counters back')
assert.equal(csrAfterUndo[RISCV_CSR_REGISTERS.indexOf('uscratch')], 0x77n, 'undo must not roll back a preset CSR')

assert.throws(() => fpu.setFloatingPointRegisterValue(32, 0, 0), /out of range/)
assert.throws(() => fpu.setFloatingPointRegisterValue(-1, 0, 0), /out of range/)
assert.throws(() => fpu.setControlAndStatusRegisterValue(17, 0, 0), /out of range/)
assert.throws(() => fpu.setControlAndStatusRegisterValue(-1, 0, 0), /out of range/)

// The files are 64 bit wide on both targets, and a single stays NaN-boxed on RV64 too.
RISCV.setIs64Bit(true)
const fpu64 = await runFpProgram()
const fp64 = readRegisterFile(fpu64.getFloatingPointRegistersValues())
assert.equal(fp64.length, 32)
assert.equal(fp64[0], NAN_BOXED_3F, 'RV64: ft0 should hold 3.0f, NaN-boxed')
assert.equal(fp64[1], DOUBLE_3, 'RV64: ft1 should hold 3.0d')
assert.equal(readRegisterFile(fpu64.getControlAndStatusRegistersValues()).length, 17)
RISCV.setIs64Bit(false)

console.log(`ok - register files: ft0=${fp[0].toString(16)}, ft1=${fp[1].toString(16)}, instret=${csrByName('instret')}`)

// ---------------------------------------------------------------------------
// Pokes: a register or memory value the host changes between two instructions, recorded in this
// same history as a step of its own and undone by the core itself.
// ---------------------------------------------------------------------------

const POKE_SOURCE = `
    .data
buf:    .word 0x11223344
        .word 0x55667788

    .text
    .globl main
main:
    li   t0, 1
    li   t1, 2
    add  t2, t0, t1
    li   t3, 4
    li   a7, 10
    ecall
`

// `.data` starts here, so `buf` is the first word of it; the peripheral section above relies on
// the same base address.
const POKE_DATA = 0x10010000

// The undo capacity is a global of the simulator and the stack is built at assembly, so every
// program here states the capacity it wants rather than inheriting the one the last test chose.
const makePokeProgram = (undoSize = 1000) => {
    const program = makeSingleFileRiscV(POKE_SOURCE)
    registerHandlers(program, Object.fromEntries(HANDLER_NAMES.map(name => [name, unimplementedHandler(name)])))
    program.setUndoSize(undoSize)
    const assembled = program.assemble()
    assert.equal(assembled.hasErrors, false, `poke assembly failed: ${assembled.report}`)
    program.initialize(true)
    return program
}

const stepTimes = async (program, count) => {
    for (let i = 0; i < count; i++) await program.step()
}

const csrOf = program => {
    const values = readRegisterFile(program.getControlAndStatusRegistersValues())
    return name => values[RISCV_CSR_REGISTERS.indexOf(name)]
}

RISCV.setIs64Bit(false)

// 1. The transaction API.
{
    const program = makePokeProgram()
    await stepTimes(program, 2)

    assert.equal(program.pokeOpen(), false, 'no poke is open to begin with')
    assert.throws(() => program.endPoke(), /No poke is open/, 'endPoke with none open throws')

    program.beginPoke()
    assert.equal(program.pokeOpen(), true)
    assert.throws(() => program.beginPoke(), /already open/, 'beginPoke inside a poke throws')
    assert.equal(program.endPoke(), false, 'a poke that wrote nothing records nothing')
    assert.equal(program.pokeOpen(), false)
    assert.throws(() => program.endPoke(), /No poke is open/, 'the transaction really closed')

    // An instruction is in flight from the moment step() is called until its promise settles, and
    // the simulator's state is half written in between.
    const inFlight = program.step()
    assert.throws(() => program.beginPoke(), /while an instruction is executing/,
        'beginPoke during an instruction throws')
    await inFlight
    program.beginPoke()
    assert.equal(program.pokeOpen(), true, 'the guard clears once the instruction has finished')
    program.endPoke()
}

// 2. Outside a transaction the setters stay direct and record nothing, which testcase presets
//    rely on; inside one they journal.
{
    const program = makePokeProgram()
    await stepTimes(program, 3)

    const observed = []
    const handle = program.addMemoryWriteObserver(POKE_DATA, POKE_DATA + 4,
        (address, length, value) => observed.push([address, length, value]))

    const stackBefore = program.getUndoStack().length
    const groupsBefore = program.getUndoGroups().length
    assert.ok(stackBefore > 0, 'the executed instructions should have recorded entries')

    program.setRegisterValue('s1', 0, 0x1234)
    program.setFloatingPointRegisterValue(2, 0, 0x99)
    program.setControlAndStatusRegisterValue(RISCV_CSR_REGISTERS.indexOf('uscratch'), 0, 0x77)
    program.setMemoryBytes(POKE_DATA, [0xAA, 0xBB])

    assert.equal(program.getUndoStack().length, stackBefore, 'a setter outside a poke records nothing')
    assert.equal(program.getUndoGroups().length, groupsBefore, 'and adds no history entry')
    // A host write is still a write as far as a memory mapped display is concerned.
    assert.deepEqual(observed, [[POKE_DATA, 1, 0xAA], [POKE_DATA + 1, 1, 0xBB]],
        'a host write outside a poke still notifies write observers')

    // The next undo reverts the instruction and nothing else: before this change each host byte
    // pushed a back step under the last instruction's address and went back with it.
    program.undo()
    assert.deepEqual(Array.from(program.readMemoryBytes(POKE_DATA, 2)), [0xAA, 0xBB],
        'undoing an instruction must not revert a host write made outside a poke')
    assert.equal(program.getRegisterValue('s1'), 0x1234, 'nor a host register write')

    program.removeMemoryObserver(handle)
}

// 3. A write that changes nothing journals nothing, and endPoke says so.
{
    const program = makePokeProgram()
    await stepTimes(program, 3)
    const stackBefore = program.getUndoStack().length

    program.beginPoke()
    program.setRegisterValue('t2', 0, 3) // t2 already holds 1 + 2
    program.setMemoryBytes(POKE_DATA, [0x44, 0x33]) // the bytes already there
    program.setFloatingPointRegisterValue(0, 0, 0)  // still zero
    program.setRegisterValue('zero', 0, 5)          // zero holds nothing to restore
    assert.equal(program.endPoke(), false, 'writing the values already held records no entry')
    assert.equal(program.getUndoStack().length, stackBefore, 'and takes no slot of the history')
    assert.equal(program.getRegisterValue('zero'), 0, 'zero stays zero')

    program.beginPoke()
    program.setRegisterValue('t2', 0, 3)
    program.setRegisterValue('t2', 0, 9) // this one does change it
    assert.equal(program.endPoke(), true, 'one changed value is enough for an entry')
    assert.equal(program.getRegisterValue('t2'), 9)
}

// 4. The entry: kind, and a writes list with old AND new values.
{
    const program = makePokeProgram()
    await stepTimes(program, 3)
    const statements = Array.from(program.getCompiledStatements())

    assert.deepEqual(Array.from(program.readMemoryBytes(POKE_DATA, 4)), [0x44, 0x33, 0x22, 0x11],
        'the data word is little endian, so the poke below writes its first two bytes')

    program.beginPoke()
    program.setRegisterValue('t2', 0, 0x55)
    program.setFloatingPointRegisterValue(RISCV_FLOATING_POINT_REGISTERS.indexOf('ft1'), 0x7FF00000, 0)
    program.setControlAndStatusRegisterValue(RISCV_CSR_REGISTERS.indexOf('fcsr'), 0, 0x60)
    program.setMemoryBytes(POKE_DATA, [1, 2])
    program.setMemoryBytes(POKE_DATA + 6, [9]) // a second, non adjacent run
    assert.equal(program.endPoke(), true)

    const groups = Array.from(program.getUndoGroups())
    const top = groups[0]
    assert.equal(top.kind, 'poke', 'the newest entry is the poke')
    assert.equal(top.pc, -1, 'a poke belongs to no instruction, so it has no address')
    assert.equal(top.steps.length, 1, 'the whole poke is one back step')
    assert.equal(top.steps[0].isPoke, true, 'and the raw stack says so too')
    assert.equal(top.steps[0].action, BackStepAction.POKE)

    // Registers in the order they were written, then each run of memory by ascending address.
    // A 64 bit register value crosses as a signed decimal string, as getRegistersValuesLong does.
    assert.deepEqual(Array.from(top.writes), [
        { type: 'register', name: 't2', old: '3', new: '85' },
        { type: 'register', name: 'ft1', old: '0', new: '9218868437227405312' },
        { type: 'register', name: 'fcsr', old: '0', new: '96' },
        { type: 'memory', address: POKE_DATA, old: [0x44, 0x33], new: [1, 2] },
        { type: 'memory', address: POKE_DATA + 6, old: [0x66], new: [9] },
    ], 'the entry reports every write with what was there and what is there now')

    assert.equal(groups[1].kind, 'instruction', 'the entry below it is an instruction')
    assert.equal(groups[1].pc, statements[2].address, 'named by the address it ran at')
    assert.deepEqual(Array.from(groups[1].writes), [], 'only a poke reports writes')

    // Entries, steps and writes are plain objects, so a history survives being cloned or
    // serialized on its way to a panel.
    assert.deepEqual(structuredClone(groups[0]), groups[0])
    assert.equal(Array.isArray(program.getUndoGroups()), true)
    assert.equal(Array.isArray(program.getUndoStack()), true)
    assert.ok(JSON.stringify(program.getUndoGroups()).includes('"kind":"poke"'))

    // The kind is never absent: every entry carries one, and every raw back step carries isPoke.
    assert.ok(groups.every(group => group.kind === 'poke' || group.kind === 'instruction'))
    assert.ok(Array.from(program.getUndoStack()).every(step => typeof step.isPoke === 'boolean'))

    // A second poke, written twice to the same register: the old value is what the register held
    // before this transaction, the new value what it holds at endPoke.
    program.beginPoke()
    program.setRegisterValue('t2', 0, 0x56)
    program.setRegisterValue('t2', 0, 0x57)
    assert.equal(program.endPoke(), true)
    assert.deepEqual(Array.from(program.getUndoGroups())[0].writes, [
        { type: 'register', name: 't2', old: '85', new: '87' },
    ], 'a register written twice in one poke reports one write, first old to last new')
}

// 4b. A 64 bit register value is reported exactly, sign and all.
{
    RISCV.setIs64Bit(true)
    const program = makePokeProgram()
    await stepTimes(program, 1)
    program.beginPoke()
    program.setRegisterValue('t1', 0xFFFFFFFF | 0, 0xFFFFFFFE | 0)
    assert.equal(program.endPoke(), true)
    const write = Array.from(program.getUndoGroups())[0].writes[0]
    assert.equal(write.new, '-2', 'a 64 bit value crosses as a signed decimal string')
    assert.equal(BigInt.asUintN(64, BigInt(write.new)), 0xFFFFFFFFFFFFFFFEn,
        'which reads back as the unsigned value highLowToBigint reports')
    assert.equal(Array.from(program.getRegistersValuesLong())[RISCV_REGISTERS.indexOf('t1')], '-2',
        'the same string the register file getter reports')
    RISCV.setIs64Bit(false)
}

// 5. A poke sits in the same history as the instructions, at its position, taking one slot.
{
    const program = makePokeProgram()
    await stepTimes(program, 3)
    const statements = Array.from(program.getCompiledStatements())
    const stackBefore = program.getUndoStack().length

    program.beginPoke()
    program.setMemoryBytes(POKE_DATA, [1, 2, 3, 4, 5, 6, 7, 8])
    assert.equal(program.endPoke(), true)
    assert.equal(program.getUndoStack().length, stackBefore + 1,
        'a poke of eight bytes is one slot of the history, not eight')

    await program.step()

    const groups = Array.from(program.getUndoGroups())
    assert.deepEqual(groups.slice(0, 3).map(group => group.kind), ['instruction', 'poke', 'instruction'],
        'newest first, with the poke between the instructions that surround it')
    assert.equal(groups[0].pc, statements[3].address)
    assert.equal(groups[2].pc, statements[2].address)
    assert.equal(program.canUndo, true)
}

// 5b. One slot means a poke can be evicted whole, never in part.
{
    const program = makePokeProgram(2)
    const firstBytes = () => Array.from(program.readMemoryBytes(POKE_DATA, 1))

    program.beginPoke(); program.setMemoryBytes(POKE_DATA, [0xA0]); assert.equal(program.endPoke(), true)
    program.beginPoke(); program.setMemoryBytes(POKE_DATA, [0xA1]); assert.equal(program.endPoke(), true)
    program.beginPoke(); program.setMemoryBytes(POKE_DATA, [0xA2]); assert.equal(program.endPoke(), true)

    const groups = Array.from(program.getUndoGroups())
    assert.equal(groups.length, 2, 'the history holds two entries, so the oldest poke is gone')
    assert.deepEqual(groups.map(group => group.kind), ['poke', 'poke'])
    assert.deepEqual(groups[0].writes, [{ type: 'memory', address: POKE_DATA, old: [0xA1], new: [0xA2] }])
    assert.deepEqual(groups[1].writes, [{ type: 'memory', address: POKE_DATA, old: [0xA0], new: [0xA1] }])

    program.undo()
    assert.deepEqual(firstBytes(), [0xA1], 'the newest poke went back whole')
    program.undo()
    assert.deepEqual(firstBytes(), [0xA0])
    assert.equal(program.canUndo, false, 'the evicted poke cannot be undone')
    assert.equal(Array.from(program.getUndoGroups()).length, 0)
}

// 6. canUndo and undo treat a poke like an instruction, and undoing one touches nothing else.
{
    const program = makePokeProgram()
    await stepTimes(program, 3)

    const before = {
        pc: program.programCounter,
        registers: Array.from(program.getRegistersValuesLong()),
        fp: readRegisterFile(program.getFloatingPointRegistersValues()).map(String),
        csr: readRegisterFile(program.getControlAndStatusRegistersValues()).map(String),
        memory: Array.from(program.readMemoryBytes(POKE_DATA, 8)),
        callStack: JSON.stringify(Array.from(program.getCallStack())),
        stopReason: program.getStopReason(),
    }
    const instretBefore = csrOf(program)('instret')
    assert.ok(instretBefore > 0n, 'the counters should have counted the instructions')

    program.beginPoke()
    program.setRegisterValue('t2', 0, 0x55)
    program.setFloatingPointRegisterValue(3, 0, 0x11)
    program.setControlAndStatusRegisterValue(RISCV_CSR_REGISTERS.indexOf('uscratch'), 0, 0x99)
    program.setMemoryBytes(POKE_DATA, [1, 2, 3, 4])
    assert.equal(program.endPoke(), true)
    assert.equal(program.canUndo, true, 'canUndo is true with a poke on top')
    assert.equal(program.programCounter, before.pc, 'making a poke does not move the program counter')

    program.undo()

    assert.equal(program.programCounter, before.pc, 'undoing a poke leaves the program counter alone')
    assert.deepEqual(Array.from(program.getRegistersValuesLong()), before.registers)
    assert.deepEqual(readRegisterFile(program.getFloatingPointRegistersValues()).map(String), before.fp)
    assert.deepEqual(readRegisterFile(program.getControlAndStatusRegistersValues()).map(String), before.csr)
    assert.deepEqual(Array.from(program.readMemoryBytes(POKE_DATA, 8)), before.memory)
    assert.equal(JSON.stringify(Array.from(program.getCallStack())), before.callStack,
        'undoing a poke leaves the call stack alone')
    assert.equal(program.getStopReason(), before.stopReason)
    assert.equal(csrOf(program)('instret'), instretBefore,
        'undoing a poke must not decrement the instruction counters')

    // A poke of a read only counter is allowed, and undoing it puts the counter back.
    const cycleBefore = csrOf(program)('cycle')
    program.beginPoke()
    program.setControlAndStatusRegisterValue(RISCV_CSR_REGISTERS.indexOf('cycle'), 0x2A, 7)
    assert.equal(program.endPoke(), true)
    assert.equal(csrOf(program)('cycle'), 0x2A00000007n)
    assert.equal(csrOf(program)('cycleh'), 0x2An, 'the linked high half follows its base')
    program.undo()
    assert.equal(csrOf(program)('cycle'), cycleBefore, 'undo puts a poked counter back')

    // And a poke of a linked register writes, and restores, the register it aliases.
    const fcsrBefore = csrOf(program)('fcsr')
    program.beginPoke()
    program.setControlAndStatusRegisterValue(RISCV_CSR_REGISTERS.indexOf('frm'), 0, 3)
    assert.equal(program.endPoke(), true)
    assert.equal(csrOf(program)('fcsr'), fcsrBefore | 0x60n, 'writing frm updates fcsr')
    program.undo()
    assert.equal(csrOf(program)('fcsr'), fcsrBefore, 'and undoing it restores fcsr')
    assert.equal(csrOf(program)('frm'), 0n)
}

// 7. A poke's identity is its own: never an instruction's address.
{
    const program = makePokeProgram()
    await stepTimes(program, 3)
    const addresses = Array.from(program.getCompiledStatements()).map(statement => statement.address)

    program.beginPoke()
    program.setRegisterValue('t2', 0, 0x55)
    assert.equal(program.endPoke(), true)
    program.beginPoke()
    program.setRegisterValue('t2', 0, 0x56)
    assert.equal(program.endPoke(), true)

    const pokes = Array.from(program.getUndoGroups()).filter(group => group.kind === 'poke')
    assert.equal(pokes.length, 2, '8. two consecutive pokes are two entries')
    for (const poke of pokes) {
        assert.equal(poke.pc, -1)
        assert.equal(addresses.includes(poke.pc), false, 'a poke never carries an instruction address')
    }
    assert.equal(program.programCounter, addresses[3],
        'and no poke key is ever written into the program counter')
}

// 8. Poke, then an instruction, then undo, undo: the instruction first, then the poke.
{
    const program = makePokeProgram()
    await stepTimes(program, 3)

    const registersBefore = Array.from(program.getRegistersValuesLong())
    const memoryBefore = Array.from(program.readMemoryBytes(POKE_DATA, 8))
    const pcBefore = program.programCounter
    const csrBefore = readRegisterFile(program.getControlAndStatusRegistersValues()).map(String)

    program.beginPoke()
    program.setRegisterValue('t2', 0, 0x55)
    program.setMemoryBytes(POKE_DATA, [1, 2, 3, 4])
    assert.equal(program.endPoke(), true)

    await program.step() // li t3, 4
    assert.equal(program.getRegisterValue('t3'), 4)

    program.undo()
    assert.equal(program.getRegisterValue('t3'), 0, 'the instruction is reverted first')
    assert.equal(program.getRegisterValue('t2'), 0x55, 'and the poke is still in place')
    assert.equal(program.programCounter, pcBefore)

    program.undo()
    assert.deepEqual(Array.from(program.getRegistersValuesLong()), registersBefore,
        'undoing the poke leaves exactly the state from before it')
    assert.deepEqual(Array.from(program.readMemoryBytes(POKE_DATA, 8)), memoryBefore)
    assert.deepEqual(readRegisterFile(program.getControlAndStatusRegistersValues()).map(String), csrBefore)
    assert.equal(program.programCounter, pcBefore)
}

// A poke into a memory mapped display repaints it, at once and again when undone, through the
// observer the display already registers.
{
    const program = makePokeProgram()
    await stepTimes(program, 3)
    const painted = []
    const handle = program.addMemoryWriteObserver(POKE_DATA, POKE_DATA + 4,
        (address, length, value) => painted.push([address, length, value]))

    program.beginPoke()
    program.setMemoryBytes(POKE_DATA, [0x44, 0xEE]) // the first byte already holds 0x44
    assert.equal(program.endPoke(), true)
    assert.deepEqual(painted, [[POKE_DATA + 1, 1, 0xEE]],
        'a byte already holding the value written is skipped, so the display is not repainted for it')

    program.undo()
    assert.deepEqual(painted.slice(1), [[POKE_DATA + 1, 1, 0x33]],
        'undoing a poke restores through the same store, so the display repaints back')
    assert.deepEqual(Array.from(program.readMemoryBytes(POKE_DATA, 2)), [0x44, 0x33])

    program.removeMemoryObserver(handle)
}

// With undo switched off the writes stand, but there is no entry to report or revert.
{
    const program = makePokeProgram()
    await stepTimes(program, 3)
    program.setUndoEnabled(false)
    program.beginPoke()
    program.setRegisterValue('t2', 0, 0x55)
    assert.equal(program.endPoke(), false, 'nothing is recorded while undo is disabled')
    assert.equal(program.getRegisterValue('t2'), 0x55, 'but the write stands')
    program.setUndoEnabled(true)
}

console.log('ok - pokes: transaction, one entry per poke, grouped history, undo')

// ---------------------------------------------------------------------------
// Written values: every back step that restores a value reports what the write left behind beside
// what it replaced, both as the simulator saw them at the store, and both whole - a 64 bit value
// crosses as a signed decimal string, where `param2` truncates it to 32 bits.
// ---------------------------------------------------------------------------

const WRITTEN_SOURCE = `
    .data
buf:    .word 0x11223344
        .word 0x55667788
        .word 0x99AABBCC

    .text
    .globl main
main:
    li   t0, 0x1234
    la   t1, buf
    sw   t0, 0(t1)
    li   t2, 0x7FFF5678
    sh   t2, 4(t1)
    li   t3, -1
    sb   t3, 8(t1)
    fcvt.s.w ft0, t0
    csrrwi t4, fcsr, 3
    jal  ra, done
    nop
done:
    li   a7, 10
    ecall
`

const WRITTEN_DATA = 0x10010000

const makeWrittenProgram = (source = WRITTEN_SOURCE) => {
    const program = makeSingleFileRiscV(source)
    registerHandlers(program, Object.fromEntries(HANDLER_NAMES.map(name => [name, unimplementedHandler(name)])))
    // The capacity is a global of the simulator, and the poke tests above leave it small enough to
    // evict the entries these assertions look back at.
    program.setUndoSize(2000)
    const assembled = program.assemble()
    assert.equal(assembled.hasErrors, false, `written-value assembly failed: ${assembled.report}`)
    program.initialize(true)
    return program
}

// The back steps one instruction recorded, newest first.
const stepRecording = async program => {
    const before = program.getUndoStack().length
    await program.step()
    const stack = Array.from(program.getUndoStack())
    return stack.slice(0, stack.length - before)
}

const runToEnd = async (program, limit = 1000) => {
    let steps = 0
    while (!program.terminated && steps < limit) {
        await program.step()
        steps++
    }
    assert.ok(program.terminated, 'program did not terminate')
}

const withAction = (steps, action) => steps.filter(step => step.action === action)

const only = (steps, action) => {
    const found = withAction(steps, action)
    assert.equal(found.length, 1, `expected exactly one ${BackStepAction[action]} step, found ${found.length}`)
    return found[0]
}

// 1. A register write reports the whole register, before and after, as the register file reads it.
{
    RISCV.setIs64Bit(false)
    const program = makeWrittenProgram()
    let registerWrites = 0
    let fpWrites = 0
    let csrWrites = 0
    while (!program.terminated && registerWrites < 100) {
        const before = Array.from(program.getRegistersValuesLong())
        const fpBefore = readRegisterFile(program.getFloatingPointRegistersValues())
        const csrBefore = csrOf(program)
        const recorded = await stepRecording(program)
        const after = Array.from(program.getRegistersValuesLong())
        const fpAfter = readRegisterFile(program.getFloatingPointRegistersValues())
        const csrAfter = csrOf(program)
        for (const step of withAction(recorded, BackStepAction.REGISTER_RESTORE)) {
            assert.equal(step.oldValue, before[step.param1],
                'a register write reports the whole register as it was before the write')
            assert.equal(step.newValue, after[step.param1],
                'and the whole register as the write left it')
            registerWrites++
        }
        for (const step of withAction(recorded, BackStepAction.FLOATING_POINT_REGISTER_RESTORE)) {
            assert.equal(BigInt.asUintN(64, BigInt(step.oldValue)), fpBefore[step.param1],
                'a floating point write reports the whole register before it')
            assert.equal(BigInt.asUintN(64, BigInt(step.newValue)), fpAfter[step.param1],
                'and after it, NaN boxing and all')
            fpWrites++
        }
        for (const step of withAction(recorded, BackStepAction.CONTROL_AND_STATUS_REGISTER_RESTORE)) {
            // `fcsr` is the only control and status register this program writes.
            assert.equal(step.param1, 3, 'param1 is still the architectural CSR number')
            assert.equal(BigInt.asUintN(64, BigInt(step.oldValue)), csrBefore('fcsr'))
            assert.equal(BigInt.asUintN(64, BigInt(step.newValue)), csrAfter('fcsr'))
            csrWrites++
        }
    }
    assert.ok(registerWrites >= 8, `expected the program to write registers, saw ${registerWrites}`)
    assert.equal(fpWrites, 1, 'fcvt.s.w records one floating point write')
    assert.equal(csrWrites, 1, 'csrrwi records one control and status write')
}

// 1b. A memory write reports the bytes it replaced and the bytes it left, at the width it was made,
// and the program counter restore the address it put back beside the address the instruction set.
{
    RISCV.setIs64Bit(false)
    const program = makeWrittenProgram()
    await runToEnd(program)
    const stack = Array.from(program.getUndoStack())

    const word = only(stack, BackStepAction.MEMORY_RESTORE_WORD)
    assert.equal(word.param1, WRITTEN_DATA, 'param1 is still the address')
    assert.equal(word.oldValue, String(0x11223344), 'the word the store replaced')
    assert.equal(word.newValue, String(0x1234), 'and the word it left')

    const half = only(stack, BackStepAction.MEMORY_RESTORE_HALF)
    assert.equal(half.param1, WRITTEN_DATA + 4)
    assert.equal(half.oldValue, String(0x7788), 'a half reports the half it replaced')
    assert.equal(half.newValue, String(0x5678),
        'and the half it left: the value is reported at the width of the write, not the whole register')

    const byte = only(stack, BackStepAction.MEMORY_RESTORE_BYTE)
    assert.equal(byte.param1, WRITTEN_DATA + 8)
    assert.equal(byte.oldValue, String(0xCC), 'a byte reports the byte it replaced')
    assert.equal(byte.newValue, String(0xFF), 'and the byte it left, so `sb` of -1 reads as 0xff')

    assert.deepEqual(Array.from(program.readMemoryBytes(WRITTEN_DATA, 12)),
        [0x34, 0x12, 0x00, 0x00, 0x78, 0x56, 0x66, 0x55, 0xFF, 0xBB, 0xAA, 0x99],
        'and what the three stores left is what memory holds')

    const pc = only(stack, BackStepAction.PC_RESTORE)
    const statements = Array.from(program.getCompiledStatements())
    const jal = statements.find(statement => statement.assemblyStatement.startsWith('jal'))
    const target = statements.find(statement => statement.address === jal.address + 8)
    assert.equal(pc.param1, jal.address, 'param1 is still the address the restore puts back')
    assert.equal(pc.oldValue, String(jal.address), 'which is what the restore reports as the old value')
    assert.equal(pc.newValue, String(target.address),
        'beside the address the instruction set: the jump target, two instructions on')
}

// 2. The values cross without loss: a 64 bit register or an `sd` truncates in `param2` and does not
// in `oldValue`/`newValue`.
{
    RISCV.setIs64Bit(true)
    const program = makeWrittenProgram(`
    .data
dbuf:   .dword 0x1122334455667788

    .text
    .globl main
main:
    li   t0, 0xFEDCBA9876543210
    la   t1, dbuf
    sd   t0, 0(t1)
    li   a7, 10
    ecall
`)
    await runToEnd(program)
    const stack = Array.from(program.getUndoStack())

    const registers = Array.from(program.getRegistersValuesLong())
    const t0 = RISCV_REGISTERS.indexOf('t0')
    const written = withAction(stack, BackStepAction.REGISTER_RESTORE)
        .find(step => step.param1 === t0 && step.newValue === registers[t0])
    assert.ok(written, 'the last write to t0 reports the whole 64 bit value it left')
    assert.equal(BigInt.asUintN(64, BigInt(written.newValue)), 0xFEDCBA9876543210n,
        'read back with BigInt, exactly')
    assert.equal(written.param2 | 0, written.param2,
        'param2 is still the 32 bit int it always was')

    const dword = only(stack, BackStepAction.MEMORY_RESTORE_DOUBLE_WORD)
    assert.equal(dword.oldValue, String(0x1122334455667788n),
        'an `sd` reports all 64 bits of what it replaced')
    assert.equal(BigInt.asUintN(64, BigInt(dword.newValue)), 0xFEDCBA9876543210n,
        'and all 64 bits of what it wrote')
    assert.equal(dword.param2, 0x55667788 | 0,
        'where param2 still hands over the low half alone, as it always has')
    assert.notEqual(dword.param2, Number(dword.oldValue),
        'which is exactly the truncation oldValue exists to undo')

    // The clock sample the simulator takes is the other 64 bit value on this stack, and it is a
    // write that lands in an instruction's entry like any other, so it reports both sides: the
    // millisecond reading it left beside the one it replaced.
    for (const step of withAction(stack, BackStepAction.CONTROL_AND_STATUS_REGISTER_BACKDOOR)) {
        assert.equal(BigInt.asIntN(32, BigInt(step.oldValue)), BigInt(step.param2),
            'param2 is the low half of the value oldValue reports whole')
        assert.ok(BigInt(step.newValue) > BigInt(step.oldValue),
            'the clock backdoor reports the reading it wrote, which is later than the one it replaced')
    }
    RISCV.setIs64Bit(false)
}

// 2b. The clock backdoor, forced: a sample is recorded only when the millisecond turned over, so
// this runs long enough for that to be certain, with a history big enough to still hold the
// samples, and checks the written value against the clock and against the next write's old value.
{
    RISCV.setIs64Bit(false)
    const ticking = makeSingleFileRiscV(`
    .text
    .globl main
main:
    li   t0, 0
loop:
    addi t0, t0, 1
    j    loop
`)
    registerHandlers(ticking, Object.fromEntries(HANDLER_NAMES.map(name => [name, unimplementedHandler(name)])))
    // The loop pushes about three entries per instruction, and the simulator samples the clock
    // every sixty fourth instruction: the history has to span more than a millisecond of simulation
    // for a recorded sample to still be on it at the end.
    ticking.setUndoSize(200_000)
    const assembled = ticking.assemble()
    assert.equal(assembled.hasErrors, false, `clock-sample assembly failed: ${assembled.report}`)
    ticking.initialize(true)
    const before = BigInt(Date.now())
    await ticking.simulateWithLimit(500_000)
    const after = BigInt(Date.now())
    const samples = withAction(Array.from(ticking.getUndoStack()),
        BackStepAction.CONTROL_AND_STATUS_REGISTER_BACKDOOR)
    assert.ok(samples.length >= 2, `expected the run to sample the clock, saw ${samples.length}`)
    for (const step of samples) {
        const wrote = BigInt(step.newValue)
        assert.ok(wrote >= before && wrote <= after,
            `the written value is the wall clock reading the simulator stored, got ${step.newValue}`)
    }
    // getUndoStack is newest first, so this walks the samples forwards in time: what one write left
    // is what the next one replaced, which no reader could reconstruct from oldValue alone.
    const ordered = samples.slice().reverse()
    for (let i = 1; i < ordered.length; i++) {
        assert.equal(ordered[i].oldValue, ordered[i - 1].newValue,
            'the reading one clock write left is the reading the next one replaced')
    }
}

// 3. The shape is additive: both fields are on every step of both read APIs, the existing fields are
// untouched, and a Poke's own writes are unchanged.
{
    RISCV.setIs64Bit(false)
    const program = makeWrittenProgram()
    await runToEnd(program)

    const isValue = value => typeof value === 'string' && /^-?\d+$/.test(value)
    const stack = Array.from(program.getUndoStack())
    assert.ok(stack.length > 0)
    for (const step of stack) {
        assert.ok(isValue(step.oldValue), `oldValue must be a signed decimal string, got ${step.oldValue}`)
        assert.ok(isValue(step.newValue), `newValue must be a signed decimal string, got ${step.newValue}`)
        assert.equal(typeof step.param1, 'number')
        assert.equal(typeof step.param2, 'number')
        assert.equal(typeof step.pc, 'number')
        assert.equal(typeof step.isPoke, 'boolean')
    }
    for (const group of Array.from(program.getUndoGroups())) {
        for (const step of group.steps) {
            assert.ok(isValue(step.oldValue) && isValue(step.newValue),
                'every step of a group carries both values too')
        }
    }
    // Own enumerable properties, so a whole history still serializes as it comes.
    assert.ok(JSON.stringify(program.getUndoStack()).includes('"newValue"'))

    // An entry that restores no single value carries neither.
    for (const step of withAction(stack, BackStepAction.CONTROL_AND_STATUS_COUNTERS_DECREMENT)) {
        assert.equal(step.oldValue, '0')
        assert.equal(step.newValue, '0')
    }
}

// 3b. A Poke reports both sides through its own writes, as it already did, and the one back step it
// is carries neither.
{
    RISCV.setIs64Bit(false)
    const program = makeWrittenProgram()
    await program.step()
    program.beginPoke()
    program.setRegisterValue('t2', 0, 0x55)
    program.setMemoryBytes(WRITTEN_DATA, [0xDE, 0xAD])
    assert.equal(program.endPoke(), true)

    const poke = Array.from(program.getUndoGroups())[0]
    assert.equal(poke.kind, 'poke')
    assert.deepEqual(poke.writes, [
        { type: 'register', name: 't2', old: '0', new: '85' },
        { type: 'memory', address: WRITTEN_DATA, old: [0x44, 0x33], new: [0xDE, 0xAD] },
    ], 'a poke reports its writes exactly as before')
    assert.equal(poke.steps.length, 1)
    assert.equal(poke.steps[0].action, BackStepAction.POKE)
    assert.equal(poke.steps[0].oldValue, '0', 'the poke entry itself carries no single value')
    assert.equal(poke.steps[0].newValue, '0')

    program.undo()
    assert.equal(program.getRegisterValue('t2'), 0)
    assert.deepEqual(Array.from(program.readMemoryBytes(WRITTEN_DATA, 2)), [0x44, 0x33])
}

// 4. The value is captured at the store: a write records the one entry it always did, with no
// second entry and no read back of what it wrote.
{
    RISCV.setIs64Bit(false)
    const program = makeWrittenProgram()
    // Step until the `sw`, whose expansion `li t0, 0x1234` and `la t1, buf` precede.
    let recorded = []
    for (let i = 0; i < 10 && withAction(recorded, BackStepAction.MEMORY_RESTORE_WORD).length === 0; i++) {
        recorded = await stepRecording(program)
    }
    assert.equal(
        withAction(recorded, BackStepAction.MEMORY_RESTORE_WORD).length, 1,
        'the store records exactly one memory entry, as it did before it reported what it wrote')
    assert.equal(recorded.filter(step =>
        step.action !== BackStepAction.MEMORY_RESTORE_WORD
        && step.action !== BackStepAction.CONTROL_AND_STATUS_COUNTERS_DECREMENT
        && step.action !== BackStepAction.CONTROL_AND_STATUS_REGISTER_BACKDOOR).length, 0,
        'and nothing else: no entry exists to carry the written value')
}

// 4b. The written value survives the history wrapping around: the stack is a ring of recycled
// entries, so this fills it many times over and checks that what is left reads as one unbroken
// chain - each write of `t0` left what the next one replaced - and that undo walks back down it.
{
    RISCV.setIs64Bit(false)
    const looping = makeSingleFileRiscV(`
    .text
    .globl main
main:
    li   t0, 0
loop:
    addi t0, t0, 1
    j    loop
`)
    registerHandlers(looping, Object.fromEntries(HANDLER_NAMES.map(name => [name, unimplementedHandler(name)])))
    const capacity = 64
    looping.setUndoSize(capacity)
    const assembled = looping.assemble()
    assert.equal(assembled.hasErrors, false, `looping assembly failed: ${assembled.report}`)
    looping.initialize(true)
    await looping.simulateWithLimit(5_000)

    const stack = Array.from(looping.getUndoStack())
    assert.equal(stack.length, capacity, 'the ring holds exactly its capacity once it has wrapped')
    const t0 = RISCV_REGISTERS.indexOf('t0')
    const writes = withAction(stack, BackStepAction.REGISTER_RESTORE).filter(step => step.param1 === t0)
    assert.ok(writes.length >= 8, `expected the loop's register writes to survive, saw ${writes.length}`)
    const ordered = writes.slice().reverse() // oldest first
    for (let i = 0; i < ordered.length; i++) {
        assert.equal(BigInt(ordered[i].newValue), BigInt(ordered[i].oldValue) + 1n,
            'each `addi t0, t0, 1` reports the value it left, one above the one it replaced')
        if (i > 0) {
            assert.equal(ordered[i].oldValue, ordered[i - 1].newValue,
                'and the value one iteration left is the value the next one replaced')
        }
    }
    // Undoing walks back down that chain: one undo reverts one instruction, and the register comes
    // back to exactly the value that instruction's entry reported having replaced.
    let checked = 0
    while (looping.canUndo && checked < 8) {
        const group = Array.from(looping.getUndoGroups())[0]
        const write = group.steps.find(step =>
            step.action === BackStepAction.REGISTER_RESTORE && step.param1 === t0)
        looping.undo()
        if (!write) continue
        assert.equal(Array.from(looping.getRegistersValuesLong())[t0], write.oldValue,
            'undoing a register write puts back exactly the value the entry reported')
        checked++
    }
    assert.equal(checked, 8, 'the wrapped history undid its register writes')
}

console.log('ok - written values: registers, memory at its width, the pc, lossless across 64 bits')
