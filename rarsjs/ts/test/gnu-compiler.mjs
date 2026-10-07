import assert from 'node:assert/strict'
import { readFile, readdir } from 'node:fs/promises'
import { RISCV, StopReason } from '../dist/index.mjs'

const directory = new URL('./fixtures/gnu-compiler/', import.meta.url)
const profile = { assemblerProfile: 'gnu-compiler-v1' }
function coreFor(source, extra = {}, options = profile) {
    const core = RISCV.makeRiscVFromFiles({ 'main.s': source, ...extra }, 'main.s', options)
    core.setUndoSize(4096)
    return core
}
function assemble(source, extra, options) {
    const core = coreFor(source, extra, options)
    const result = core.assemble()
    assert.equal(result.hasErrors, false, result.errors.map(e => e.message).join('\n'))
    core.setUndoEnabled(true)
    return core
}
function reject(source, pattern) {
    const result = coreFor(source).assemble()
    assert.equal(result.hasErrors, true, source)
    assert.match(result.errors.map(e => e.message).join('\n'), pattern)
}
const bytes = (core, address, length) => address >= 0x00400000 && address < 0x10000000
    ? Array.from({length}, (_, index) => {
        const at = address + index
        const word = core.getStatementAtAddress(at & ~3)?.binaryStatement ?? 0
        return (word >>> ((at & 3) * 8)) & 255
    }) : Array.from(core.readMemoryBytes(address, length))
const register = (core, number) => BigInt(core.getRegistersValuesLong()[number])
async function execute(core, expected, limit = 2000, seeds = {}) {
    core.initialize(true)
    for (const [name, value] of Object.entries(seeds)) core.setRegisterValue(name, 0, value)
    let reason
    for (let steps = 0; steps < limit; steps++) {
        reason = await core.step()
        if (reason === StopReason.NORMAL_TERMINATION) break
        assert.equal(reason, StopReason.MAX_STEPS, `unexpected stop ${reason}`)
    }
    assert.equal(reason, StopReason.NORMAL_TERMINATION)
    assert.equal(register(core, 10), BigInt(expected))
}
function prepared(fixture) {
    const lines = ['.text', '.globl main', 'main:', 'andi sp,sp,-16', 'call __asm_editor_main', 'li a7,93', 'ecall']
    let discard = false
    for (const { text } of fixture.response.asm) {
        const section = /^\s*\.section\s+(?:"([^"]+)"|([^\s,]+))/.exec(text)?.slice(1).find(Boolean)
        if (section) discard = /^\.(debug|zdebug|note|comment|eh_frame)/.test(section)
        else if (/^\s*\.(text|data|bss|rodata)\b/.test(text)) discard = false
        if (!discard) lines.push(text)
    }
    return lines.join('\n')
}

for (const invalid of [undefined, null, '', 'gnu-compiler-v2', 1]) {
    assert.throws(() => coreFor('', {}, { assemblerProfile: invalid }), /Unsupported assembler profile/)
}
assert.throws(() => coreFor('', {}, null), /Assembly options/)

for (const width of [32, 64]) {
    RISCV.setIs64Bit(width === 64)
    const layout = await readFile(new URL('layout.s', directory), 'utf8')
    const oracle = JSON.parse(await readFile(new URL(`layout-rv${width}.json`, directory)))
    let core = assemble(layout)
    for (const [name, address] of Object.entries(oracle.symbols)) assert.equal(core.getAddressOfLabel(name), address, name)
    for (const section of oracle.sections) assert.deepEqual(bytes(core, section.address, section.bytes.length), section.bytes, section.name)
    await execute(core, 42)

    core = assemble('.data\n.byte 1\nword: .word 2\n')
    assert.equal(core.getAddressOfLabel('word'), 0x10010001)
    const legacy = assemble('.data\n.byte 1\nword: .word 2\n', {}, {})
    assert.equal(legacy.getAddressOfLabel('word'), 0x10010004)
    const explicitLegacy = assemble('.data\n.byte 1\nword: .word 2\n', {}, { assemblerProfile: 'rars' })
    assert.deepEqual(bytes(legacy, 0x10010000, 8), bytes(explicitLegacy, 0x10010000, 8))

    core = assemble('.data\n.set N, (2+3)\n.byte 1\n.p2align 3,255,2\na: .quad b+4\n.balign 16,,16\nb: .8byte -1\n.ascii "é\\200\\xFF", "\\\"\\\\"\nend:\n.word end-b\n.comm common,12,8\n')
    assert.equal(core.getAddressOfLabel('a'), 0x10010001, 'max-skip suppresses padding')
    assert.equal(core.getAddressOfLabel('b'), 0x10010010)
    assert.deepEqual(bytes(core, 0x10010001, 8), [20, 0, 1, 16, 0, 0, 0, 0])
    assert.deepEqual(bytes(core, 0x10010018, 6), [195, 169, 128, 255, 34, 92])
    assert.deepEqual(bytes(core, core.getAddressOfLabel('common'), 12), Array(12).fill(0))

    // Separated high/low users, I and S formats, aliases and include-local numeric labels.
    core = assemble('.text\n.globl main\nmain:\n.include "inc.s"\nli a7,93\necall\n.data\nvalue: .word 41\n', {
        'inc.s': '1: auipc t0,%pcrel_hi(value)\nnop\naddi t1,t0,%pcrel_lo(1b)\nlw a0,%pcrel_lo(1b)(t0)\naddi a0,a0,1\nsw a0,%pcrel_lo(1b)(t0)\nj 2f\nli a0,0\n2: nop\n'
    })
    const statements = core.getCompiledStatements()
    assert.ok(statements.some(s => s.sourcePath === 'inc.s' && s.sourceLine === 1))
    core.initialize(true)
    const states = []
    let reason
    while (reason !== StopReason.NORMAL_TERMINATION && states.length < 30) {
        states.push({ pc: core.programCounter, registers: Array.from(core.getRegistersValuesLong()), memory: bytes(core, 0x10010000, 4) })
        reason = await core.step()
    }
    assert.equal(register(core, 10), 42n)
    for (const before of states.reverse()) {
        core.undo()
        assert.equal(core.programCounter, before.pc)
        assert.deepEqual(Array.from(core.getRegistersValuesLong()), before.registers)
        assert.deepEqual(bytes(core, 0x10010000, 4), before.memory)
    }

    core = assemble('.text\nmain: nop\n.p2align 4\nli a0,42\nli a7,93\necall\n')
    assert.equal(core.getCompiledStatements().filter(s => s.sourceLine === 3).length, 3, 'NOP padding retains directive identity')
    await execute(core, 42)
    core = assemble('.text\nmain: nop\n.p2align 3,255\n')
    assert.deepEqual(bytes(core, 0x00400004, 4), [255, 255, 255, 255])
    core.initialize(true)
    await core.step()
    await assert.rejects(() => core.step(), /undefined instruction/)

    for (const [source, message] of [
        ['.text\naddi a0,zero,42;ecall', /Multiple GNU statements/],
        ['.quad 42', /data section/],
        ['.data\n.word missing+4', /Unresolved symbol: missing/],
        ['.data\n.quad 1<<8', /Unsupported expression/],
        ['.set x,y\n.set y,x', /Cyclic/],
        ['.set x,1\n.set x,2', /Duplicate/],
        ['.data\n.p2align missing', /Unresolved symbol/],
        ['.data\nlabel: .zero label', /Layout expression/],
        ['.option pop', /underflow/],
        ['.option pic', /Unsupported GNU option/],
        ['.option rvc', /Unsupported GNU option/],
        ['.section .tdata,"awT",@progbits', /Unsupported section/],
        ['.section .init_array.first,"aw"', /Unsupported section/],
        ['.section .text.foo,"axG",@progbits,foo', /Only COMDAT/],
        ['.section .text.foo,"axG",@progbits', /flags/],
        ['.insn 0', /Unsupported GNU directive/],
        ['.text\n.word 19', /data section/],
        ['.text\nauipc a0,%got_pcrel_hi(x)', /Unsupported.*modifier/],
        ['.text\nanchor: auipc t0,%pcrel_hi(value)\naddi t0,t0,%pcrel_lo(anchor+4)\n.data\nvalue: .word 0', /zero addend/],
        ['.text\nanchor: auipc t0,%pcrel_hi(value)\n.section .text.other,"ax",@progbits\naddi t0,t0,%pcrel_lo(anchor)\n.data\nvalue: .word 0', /cross-section/],
        ['.text\naddi a0,a0,%pcrel_lo(main)\nmain: nop', /Missing/],
        ['.data\n.ascii "\\x"', /Empty hex/],
        ['.data\n.ascii "\\400"', /exceeds one byte/],
        ['.bss\n.word 1', /Nonzero initializer/],
        ['.section .debug_info,"",@progbits\ndebug: .byte 1\n.data\n.word debug', /discarded section/],
        ['.comm a,4\n.comm a,8', /Competing common/],
        ['.comm 123,4', /Invalid common symbol/],
        ['.section .rodata.foo,"aM",@progbits,4\n.section .rodata.foo,"aM",@progbits,8', /Conflicting repeated/],
        [`.attribute arch,"rv${width}i2p1_m99p0"`, /Unsupported architecture/],
        ['.data\na:.word 0\n.text\nb:nop\n.data\n.word b-a', /same section/],
        ['.data\n.byte target\ntarget:.byte 0', /overflows/]
        ,['.data\nvalue:.word 0\n.quad value-0x10010000', /outside mapped memory/]
        ,['.text\namoadd.w a0,a1,(a2)', /Atomic instructions/]
    ]) reject(source, message)

    // Global labels reach the Core's symbol table, so starting at main does not need main first.
    core = assemble('.text\nhelper: li a0,1\nret\n.globl main\nmain: li a0,42\nli a7,93\necall\n')
    await execute(core, 42)

    // C++ shapes: a constructor in .init_array run through the ld-provided bounds, a weak
    // inline function in a COMDAT group with a weak .set alias, and an undefined weak
    // reference (as a vtable's __cxa_pure_virtual slot) resolving to zero. The Core always
    // starts at a global main when there is one, so this program calls its entry program and
    // starts at _start, the first text address, until the start label becomes configurable.
    {
        const [load, store, pointer, align] = width === 64 ? ['ld', 'sd', '.dword', 3] : ['lw', 'sw', '.word', 2]
        core = assemble([
            '.text', '.globl _start', '_start:',
            'lla s0,__init_array_start', 'lla s1,__init_array_end',
            '1: beq s0,s1,2f', `${load} t0,0(s0)`, 'jalr t0', `addi s0,s0,${width / 8}`, 'j 1b',
            '2: call program', 'li a7,93', 'ecall',
            '.section .text._Z3getv,"axG",@progbits,_Z3getv,comdat', '.weak _Z3getv', '_Z3getv:',
            'lla a0,counter', 'lw a0,0(a0)', 'ret',
            '.set _Z5aliasv,_Z3getv', '.weak _Z5aliasv',
            '.text', '.globl program', 'program:', 'addi sp,sp,-16', `${store} ra,8(sp)`,
            'call _Z5aliasv', 'lla t0,slot', `${load} t1,0(t0)`, 'add a0,a0,t1',
            `${load} ra,8(sp)`, 'addi sp,sp,16', 'ret',
            'init:', 'lla t0,counter', 'li t1,42', 'sw t1,0(t0)', 'ret',
            '.section .init_array,"aw"', `.align ${align}`, `${pointer} init`,
            '.data', `.align ${align}`, 'counter: .word 0', `.align ${align}`, `slot: ${pointer} __cxa_pure_virtual`,
            '.weak __cxa_pure_virtual'
        ].join('\n'))
        assert.equal(core.getAddressOfLabel('_Z5aliasv'), core.getAddressOfLabel('_Z3getv'))
        assert.equal(core.getAddressOfLabel('__init_array_start'), -1, 'ld-provided bounds are not labels')
        core.initialize(false)
        let stop
        for (let steps = 0; steps < 200 && stop !== StopReason.NORMAL_TERMINATION; steps++) stop = await core.step()
        assert.equal(stop, StopReason.NORMAL_TERMINATION)
        assert.equal(register(core, 10), 42n)
    }

    // Constructors run in priority order, as ld sorts .init_array.NNNNN before the plain section.
    {
        const [load, pointer, align, step] = width === 64 ? ['ld', '.dword', 3, 8] : ['lw', '.word', 2, 4]
        core = assemble([
            '.text', '.globl _start', '_start:', 'li s2,0',
            'lla s0,__init_array_start', 'lla s1,__init_array_end',
            '1: beq s0,s1,2f', `${load} t0,0(s0)`, 'jalr t0', `addi s0,s0,${step}`, 'j 1b',
            '2: mv a0,s2', 'li a7,93', 'ecall',
            'last: li t1,10', 'mul s2,s2,t1', 'addi s2,s2,3', 'ret',
            'first: li t1,10', 'mul s2,s2,t1', 'addi s2,s2,1', 'ret',
            'middle: li t1,10', 'mul s2,s2,t1', 'addi s2,s2,2', 'ret',
            '.section .init_array,"aw"', `.align ${align}`, `${pointer} last`,
            '.section .init_array.00300,"aw"', `.align ${align}`, `${pointer} middle`,
            '.section .init_array.00200,"aw"', `.align ${align}`, `${pointer} first`,
        ].join('\n'))
        core.initialize(false)
        let stop
        for (let steps = 0; steps < 200 && stop !== StopReason.NORMAL_TERMINATION; steps++) stop = await core.step()
        assert.equal(register(core, 10), 123n, 'priority 200, then 300, then the plain section')
    }

    // Floating point comparisons order the infinities (an upstream bug put +inf below every
    // finite number, so isfinite(inf), compiled as |x| <= DBL_MAX, was true).
    core = assemble('.data\n.align 3\nmax: .word -1\n.word 2146435071\n.text\n.globl main\nmain: li a0,1\nfcvt.d.w fa0,a0\nfcvt.d.w fa1,zero\nfdiv.d fa4,fa0,fa1\nfneg.d fa3,fa4\nlui a4,%hi(max)\nfld fa5,%lo(max)(a4)\nfle.d a1,fa4,fa5\nflt.d a2,fa4,fa5\nfle.d a3,fa5,fa4\nflt.d a4,fa3,fa5\nflt.d a5,fa3,fa4\nslli a3,a3,2\nslli a4,a4,3\nslli a5,a5,4\nadd a0,a1,a2\nadd a0,a0,a3\nadd a0,a0,a4\nadd a0,a0,a5\nli a7,93\necall\n')
    await execute(core, 4 + 8 + 16)
    // Fused multiply-add with a zero or an infinite addend, and an exact cancellation to +0:
    // Clang contracts a*b+c into fmadd, and an upstream bug asked the zero and the infinity for an
    // exact value, which they have none of.
    core = assemble('.text\n.globl main\nmain: li a0,3\nfcvt.d.w fa0,a0\nli a1,5\nfcvt.d.w fa1,a1\nfcvt.d.w fa2,zero\nfmadd.d fa3,fa0,fa1,fa2\nfcvt.w.d a2,fa3\nli t0,1\nfcvt.d.w ft0,t0\nfdiv.d fa4,ft0,fa2\nfmadd.d fa5,fa0,fa1,fa4\nfeq.d a3,fa5,fa4\nli t1,-15\nfcvt.d.w ft1,t1\nfmadd.d fa6,fa0,fa1,ft1\nfclass.d a4,fa6\nslli a3,a3,8\nslli a4,a4,9\nadd a0,a2,a3\nadd a0,a0,a4\nli a7,93\necall\n')
    await execute(core, 15 + 256 + (16 << 9))
    // Clang marks unreachable code with unimp, which GNU as encodes as a write to the read-only
    // cycle CSR: an illegal instruction when it is reached.
    core = assemble('.text\n.globl main\nmain: unimp\n')
    assert.deepEqual(bytes(core, 0x00400000, 4), [0x73, 0x10, 0x00, 0xc0])
    // A conditional branch out of its +-4 KiB range is relaxed as GNU as relaxes it: the inverted
    // branch over a jal, with everything after it laid out again.
    core = assemble('.text\n.globl main\nmain: li a0,7\nbnez a0,far\nli a0,1\n.p2align 13\nfar: li a0,42\nli a7,93\necall\n')
    await execute(core, 42)
    core = assemble('.text\nmain: li a0,N\nli a7,93\necall\n.equ N,M+2\n.set M,40\n')
    await execute(core, 42)
    for (const value of [2047,2048,6144,-2048,-2049]) {
        core = assemble(`.text\nmain: lui a0,%hi(${value})\naddi a0,a0,%lo(${value})\nli a7,93\necall\n`)
        await execute(core, value)
    }

    for (const value of width === 64 ? ['-9223372036854775808', '9223372036854775807', '0x123456789abcdef0'] : ['-2147483648', '2147483647', '0xffffffff']) {
        core = assemble(`.text\nmain: li a0,${value}\nli a7,93\necall\n`)
        await execute(core, BigInt.asIntN(width, BigInt(value)))
    }

    const files = (await readdir(directory)).filter(name => /^(gcc|clang|rustc).*\.json$/.test(name))
    for (const filename of files) {
        const fixture = JSON.parse(await readFile(new URL(filename, directory)))
        if (fixture.width !== width) continue
        if (fixture.expectedDiagnostic) {
            reject(prepared(fixture), new RegExp(fixture.expectedDiagnostic))
            continue
        }
        core = assemble(prepared(fixture))
        assert.ok(fixture.oracle, `${filename}: stored independent oracle is required`)
        {
            for (const [name, address] of Object.entries(fixture.oracle.symbols)) assert.equal(core.getAddressOfLabel(name), address, `${filename}: ${name}`)
            for (const section of fixture.oracle.sections) assert.deepEqual(bytes(core, section.address, section.bytes.length), section.bytes, `${filename}: ${section.name}`)
        }
        const saved = [8,9,18,19,20,21,22,23,24,25,26,27]
        await execute(core, fixture.expected, 2000, Object.fromEntries(saved.map((_, i) => [`s${i}`, 100+i])))
        // Compiler startup aligns SP; each function must restore it and callee-saved registers.
        assert.equal(register(core, 2), 0x7fff_eff0n, filename)
        for (const [index, number] of saved.entries()) assert.equal(register(core, number), BigInt(100+index), `${filename}: s register ${number}`)
        assert.ok(core.getCompiledStatements().some(s => s.sourcePath === 'main.s'))
    }

    // The heap follows static data: once a GNU-profile program's .data, .rodata, .bss and common
    // symbols reach past RARS's heap base (0x10040000), sbrk's first block is the first 4 KiB page
    // after them, as ld and a kernel place the break. A 512 KiB .bss array and a common block, in
    // GCC's shapes, end at 0x10091008 here, so the heap starts at 0x10092000.
    {
        const HEAP_BASE = 0x10040000
        const program = [
            '.text', '.globl main', 'main:',
            'li a0,16', 'li a7,9', 'ecall', 'mv s0,a0',
            'li a0,8', 'li a7,9', 'ecall', 'mv s1,a0',
            'lla t0,big', 'li t1,524284', 'add t0,t0,t1', 'li t2,7', 'sw t2,0(t0)',
            'lla t1,more', 'li t2,4088', 'add t1,t1,t2', 'li t3,11', 'sw t3,0(t1)',
            'li t3,9', 'sw t3,0(s0)', 'sw t3,12(s0)',
            'lw a1,0(t0)', 'lw a2,0(t1)', 'lw a3,0(s0)', 'add a0,a1,a2', 'add a0,a0,a3',
            'li a7,93', 'ecall',
            '.data', '.align 2', 'first: .word 1',
            '.bss', '.align 2', '.type big, @object', '.size big, 524288', 'big: .zero 524288',
            '.local more', '.comm more,4096,8',
        ].join('\n')
        const heap = assemble(program)
        assert.equal(heap.getAddressOfLabel('big'), 0x10010004)
        assert.equal(heap.getAddressOfLabel('more'), 0x10010008 + 524288)
        assert.equal(heap.getHeapStart(), 0x10092000, 'the first page after static data')
        for (let runs = 0; runs < 2; runs++) {
            await execute(heap, 27)
            assert.equal(register(heap, 8), 0x10092000n, 'sbrk hands out the heap start first')
            assert.equal(register(heap, 9), 0x10092010n, 'and the next block after it')
            assert.equal(heap.exitCode, 27, 'the array, the common block and the heap hold their own values')
        }
        // The largest static data the data segment holds, which leaves the heap empty.
        assert.equal(assemble('.bss\nbig: .zero 4128768\n').getHeapStart(), 0x10400000)
        reject('.bss\nbig: .zero 4128769\n',
            /^Static data ends at 0x10400001, past the end of the data segment at 0x10400000: \.data, \.rodata, \.bss and common symbols together fit in 4128768 bytes from 0x10010000$/m)
        reject('.data\n.word 1\n.bss\n.comm huge,4194304,4\n', /Static data ends at 0x10410004, past the end of the data segment/)
        // Static data below the heap base leaves the heap where RARS has it.
        assert.equal(assemble('.bss\nbig: .zero 196608\n').getHeapStart(), HEAP_BASE)
        assert.equal(assemble('.bss\nbig: .zero 196609\n').getHeapStart(), 0x10041000)

        // A RARS-dialect program keeps RARS's layout whatever its size: the heap starts at its base,
        // inside the program's data.
        const legacy = assemble('.data\nbig: .space 524288\n.text\nmain: li a0,16\nli a7,9\necall\nmv s0,a0\nli a7,10\necall\n', {}, {})
        assert.equal(legacy.getHeapStart(), HEAP_BASE)
        legacy.initialize(true)
        while (!legacy.terminated) await legacy.step()
        assert.equal(register(legacy, 8), BigInt(HEAP_BASE))
        // And library members linked after it still stop before RARS's heap base.
        const member = { members: { 'lib/table.s': '.data\n.globl table\ntable: .word 1\n' }, index: { table: 'lib/table.s' } }
        const crossing = coreFor('.data\nbig: .space 196608\n.text\nmain: la t0,table\nli a7,10\necall\n', {}, { libraries: [member] }).assemble()
        assert.equal(crossing.hasErrors, true)
        assert.match(crossing.errors.map(e => e.message).join('\n'), /Static data reaches the heap at 0x10040000/)
    }
    console.log(`ok - GNU compiler v1 RV${width}: reference bytes, data, relocations, profiles, diagnostics, execution, undo and the heap after static data`)
}
