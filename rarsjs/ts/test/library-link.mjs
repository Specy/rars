// Library members, the entry symbol, standard input, lseek and a large lazily allocated history.
import assert from 'node:assert/strict'
import { RISCV, StopReason } from '../dist/index.mjs'

const members = {
    '@runtime/v1/twice.s': '.text\n.globl twice\ntwice:\nmv t2,ra\ncall helper\nslli a0,a0,1\njr t2\n',
    '@runtime/v1/helper.s': '.text\n.globl helper\nhelper:\naddi a0,a0,1\nret\n',
    '@runtime/v1/unused.s': '.text\n.globl unused\nunused:\nret\n',
    '@runtime/v1/maybe.s': '.data\n.globl maybe\nmaybe:\n.word 7\n',
    '@runtime/v1/crt0.s': '.text\n.globl _start\n_start:\ncall main\nli a7,93\necall\n',
    '@runtime/v1/both.s': '.text\n.globl other\n.globl dup\nother:\nret\ndup:\nret\n',
}
const library = {
    members,
    index: {
        twice: '@runtime/v1/twice.s', helper: '@runtime/v1/helper.s', unused: '@runtime/v1/unused.s',
        maybe: '@runtime/v1/maybe.s', _start: '@runtime/v1/crt0.s', other: '@runtime/v1/both.s', dup: '@runtime/v1/both.s',
    },
}
const gnu = { assemblerProfile: 'gnu-compiler-v1' }

function core(source, options = {}) {
    const riscv = RISCV.makeRiscVFromFiles({ 'main.s': source }, 'main.s', options)
    riscv.setUndoSize(4096)
    return riscv
}
function assemble(source, options) {
    const riscv = core(source, options)
    const result = riscv.assemble()
    assert.equal(result.hasErrors, false, result.errors.map(e => `${e.sourcePath}:${e.sourceLine} ${e.message}`).join('\n'))
    riscv.setUndoEnabled(true)
    return riscv
}
function errors(source, options) {
    const result = core(source, options).assemble()
    assert.equal(result.hasErrors, true, source)
    return result.errors.map(e => e.message).join('\n')
}
async function run(riscv, limit = 500) {
    riscv.initialize(true)
    let reason
    for (let steps = 0; steps < limit && reason !== StopReason.NORMAL_TERMINATION; steps++) reason = await riscv.step()
    assert.equal(reason, StopReason.NORMAL_TERMINATION)
    return BigInt(riscv.getRegistersValuesLong()[10])
}
const sourcesOf = riscv => new Set(riscv.getCompiledStatements().map(s => s.sourcePath))

for (const width of [32, 64]) {
    RISCV.setIs64Bit(width === 64)

    // A compiled program: _start pulls the startup member, which calls the program's main;
    // main pulls twice, which pulls helper in turn. Nothing pulls unused.
    const program = '.text\n.globl main\nmain:\nli a0,20\nmv s1,ra\ncall twice\nmv ra,s1\nret\n'
    let riscv = assemble(program, { ...gnu, libraries: [library], entrySymbol: '_start' })
    assert.deepEqual([...sourcesOf(riscv)].sort(), ['@runtime/v1/crt0.s', '@runtime/v1/helper.s', '@runtime/v1/twice.s', 'main.s'])
    assert.equal(riscv.getAddressOfLabel('main'), 0x00400000, 'user units come first')
    assert.ok(riscv.getAddressOfLabel('_start') > riscv.getAddressOfLabel('main'))
    assert.equal(await run(riscv), 42n, 'execution starts at the entry symbol')

    // Without an entry symbol the global main starts, and nothing pulls the startup member.
    riscv = assemble(program + 'li a7,93\necall\n', { ...gnu, libraries: [library] })
    assert.equal(sourcesOf(riscv).has('@runtime/v1/crt0.s'), false)

    // A user definition comes first; a weak reference pulls nothing and is zero.
    riscv = assemble('.text\n.globl main\nmain:\nli a0,1\ncall helper\nli a7,93\necall\n.globl helper\nhelper:\naddi a0,a0,41\nret\n.data\n.weak maybe\n.word maybe\n',
        { ...gnu, libraries: [library] })
    assert.deepEqual([...sourcesOf(riscv)], ['main.s'])
    assert.deepEqual(Array.from(riscv.readMemoryBytes(0x10010000, 4)), [0, 0, 0, 0])
    assert.equal(await run(riscv), 42n)

    // A weak reference pulls nothing, unless the library says it always supplies the symbol.
    const weakly = '.text\n.globl main\nmain:\nret\n.data\n.weak maybe\n.word maybe\n'
    riscv = assemble(weakly, { ...gnu, libraries: [library] })
    assert.deepEqual(Array.from(riscv.readMemoryBytes(0x10010000, 4)), [0, 0, 0, 0])
    riscv = assemble(weakly, { ...gnu, libraries: [{ ...library, resolveWeak: ['maybe', 'notindexed'] }] })
    const maybe = riscv.getAddressOfLabel('maybe')
    assert.ok(maybe > 0x10010000, 'the data member was pulled')
    assert.deepEqual(Array.from(riscv.readMemoryBytes(0x10010000, 4)), [maybe & 255, (maybe >> 8) & 255, (maybe >> 16) & 255, maybe >>> 24])

    assert.match(errors('.text\n.globl main\nmain:\ncall other\n.globl dup\ndup:\nret\n', { ...gnu, libraries: [library] }),
        /Multiple definition of dup/)
    assert.match(errors('.text\n.globl main\nmain:\ncall nowhere\n', { ...gnu, libraries: [library] }), /Unresolved symbol: nowhere/)
    assert.match(errors('.text\n.globl main\nmain:\nret\n', { ...gnu, entrySymbol: 'missing' }), /Undefined entry symbol: missing/)
    assert.throws(() => core('', { libraries: [{ members: {}, index: { x: 'nope.s' } }] }), /missing member/)
    assert.throws(() => core('', { entrySymbol: '1bad' }), /Invalid entry symbol/)

    // A RARS-dialect program calling the library keeps its own addresses; the members follow.
    const manual = '.data\nptr: .word helper\n.text\nmain:\nli a0,20\njal twice\nli a7,93\necall\n'
    riscv = assemble(manual, { libraries: [library] })
    assert.equal(riscv.getAddressOfLabel('main'), 0x00400000)
    assert.equal(riscv.getAddressOfLabel('ptr'), 0x10010000)
    const helper = riscv.getAddressOfLabel('helper')
    assert.ok(helper > 0x0040000c && riscv.getAddressOfLabel('twice') > 0x0040000c, 'members follow the program text')
    assert.deepEqual(Array.from(riscv.readMemoryBytes(0x10010000, 4)), [helper & 255, (helper >> 8) & 255, (helper >> 16) & 255, helper >>> 24])
    assert.equal(await run(riscv), 42n)

    // A program that needs no member assembles exactly as it does without the library.
    const plain = '.data\nvalue: .word 5\n.text\nmain:\nli a0,1\nla t0,value\nli a7,93\necall\n'
    const words = riscv => riscv.getCompiledStatements().map(s => [s.address, s.binaryStatement])
    assert.deepEqual(words(assemble(plain, { libraries: [library] })), words(assemble(plain)))
    // A local label of the program satisfies its own reference before any member.
    riscv = assemble('.text\nmain:\nli a0,5\njal helper\nli a7,93\necall\nhelper:\naddi a0,a0,37\nret\n', { libraries: [library] })
    assert.deepEqual([...sourcesOf(riscv)], ['main.s'])
    assert.equal(await run(riscv), 42n)

    // Index building: what a unit defines and what it needs from elsewhere.
    const symbols = RISCV.analyzeGnuUnit('u.s', '.text\n.globl f\n.weak g\nf:\ncall h\nla a0,.LC0\ng:\nret\n.data\n.LC0: .word k\n.weak w\n.word w\n')
    assert.deepEqual(symbols, { defined: ['f', 'g'], weak: ['g'], references: ['h', 'k'], errors: [] })
    assert.match(RISCV.analyzeGnuUnit('u.s', '.bogus\n').errors[0], /^u\.s:1: Unsupported GNU directive/)
    // A CSR or rounding-mode name is a symbol except where the instruction takes one: a library
    // function named time or cycle is pulled, while csrr still reads the CSR.
    const named = {
        members: {
            '@runtime/v1/time.s': '.text\n.globl time\ntime:\nli a0,5\nret\n',
            '@runtime/v1/cycle.s': '.data\n.globl cycle\ncycle:\n.word 6\n',
            '@runtime/v1/rtz.s': '.data\n.globl rtz\nrtz:\n.word 7\n',
        },
        index: { time: '@runtime/v1/time.s', cycle: '@runtime/v1/cycle.s', rtz: '@runtime/v1/rtz.s' },
    }
    riscv = assemble('.text\n.globl main\nmain:\ncsrr t0,time\nfcvt.w.s t1,ft0,rtz\ncall time\nlui a1,%hi(cycle)\nlw a1,%lo(cycle)(a1)\nadd a0,a0,a1\nlla a1,ref\nlw a1,0(a1)\nlw a1,0(a1)\nadd a0,a0,a1\nli a7,93\necall\n.data\nref: .word rtz\n', { ...gnu, libraries: [named] })
    assert.deepEqual([...sourcesOf(riscv)].sort(), ['@runtime/v1/time.s', 'main.s'])
    assert.equal(await run(riscv), 18n)
    assert.deepEqual(RISCV.analyzeGnuUnit('u.s', '.text\ncsrr a0,cycle\nfcvt.w.s a0,fa0,rtz\ncall time\n.data\n.word rtz\n').references, ['time', 'rtz'])
    // Numbers and numeric labels are not references; a C++ inline variable's type is accepted.
    assert.deepEqual(
        RISCV.analyzeGnuUnit('u.s', '.text\n1:\nli a0,0x55\nli a1,12\nbnez a0,1b\n.section .data.v,"awG",@progbits,v,comdat\n.weak v\n.type v, @gnu_unique_object\nv: .word 0x1f\n'),
        { defined: ['v'], weak: ['v'], references: [], errors: [] }
    )

    // Standard input: a count per read, and 0 at end of input.
    riscv = assemble('.data\nbuf: .space 8\n.text\nmain:\nli a0,0\nla a1,buf\nli a2,8\nli a7,63\necall\nmv s0,a0\nli a0,0\nla a1,buf\nli a2,8\nli a7,63\necall\nmv s1,a0\nli a7,10\necall\n')
    const answers = [[3, [104, 105, 10]], [0, []]]
    riscv.initialize(true)
    riscv.registerHandler('stdIn', async (_buffer, length) => {
        assert.equal(length, 8)
        return answers.shift()
    })
    let reason
    for (let steps = 0; steps < 40 && reason !== StopReason.NORMAL_TERMINATION; steps++) reason = await riscv.step()
    const registers = riscv.getRegistersValuesLong()
    assert.equal(BigInt(registers[8]), 3n)
    assert.equal(BigInt(registers[9]), 0n)
    assert.deepEqual(Array.from(riscv.readMemoryBytes(0x10010000, 3)), [104, 105, 10])

    // lseek reaches the host, which owns the files; the standard streams cannot seek.
    riscv = assemble('.text\nmain:\nli a0,5\nli a1,-2\nli a2,2\nli a7,62\necall\nmv s0,a0\nli a0,1\nli a1,0\nli a2,0\nli a7,62\necall\nmv s1,a0\nli a7,10\necall\n')
    riscv.initialize(true)
    const seeks = []
    riscv.registerHandler('seekFile', (fd, offset, whence) => { seeks.push([fd, offset, whence]); return 98 })
    reason = undefined
    for (let steps = 0; steps < 40 && reason !== StopReason.NORMAL_TERMINATION; steps++) reason = await riscv.step()
    assert.deepEqual(seeks, [[5, -2, 2]])
    assert.equal(BigInt(riscv.getRegistersValuesLong()[8]), 98n)
    assert.equal(BigInt.asIntN(32, BigInt(riscv.getRegistersValuesLong()[9])), -1n)

    // A history large enough for a library call costs nothing until it fills.
    riscv = RISCV.makeRiscVFromFiles({ 'main.s': '.text\nmain:\nli t0,3000\nloop:\naddi t0,t0,-1\nbnez t0,loop\nli a7,10\necall\n' }, 'main.s')
    riscv.setUndoSize(2_000_000)
    const started = performance.now()
    assert.equal(riscv.assemble().hasErrors, false)
    assert.ok(performance.now() - started < 1000, 'assembly does not allocate the whole history')
    riscv.setUndoEnabled(true)
    riscv.initialize(true)
    reason = undefined
    let steps = 0
    for (; steps < 10000 && reason !== StopReason.NORMAL_TERMINATION; steps++) reason = await riscv.step()
    // Reading the newest groups never builds the rest of a large history.
    assert.equal(riscv.getUndoDepth(), steps)
    const all = riscv.getUndoGroups()
    assert.equal(all.length, steps)
    const range = riscv.getUndoGroupsRange(steps - 3, 2)
    assert.deepEqual(range.map(g => g.pc), all.slice(steps - 3, steps - 1).map(g => g.pc))
    assert.deepEqual(riscv.getUndoGroupsUpTo(4).map(g => g.pc), all.slice(0, 4).map(g => g.pc))
    assert.equal(riscv.getUndoGroupsRange(steps + 5, 3).length, 0)
    for (let i = 0; i < steps; i++) riscv.undo()
    assert.equal(riscv.getUndoDepth(), 0)
    assert.equal(riscv.programCounter, 0x00400000, 'every instruction undoes')
    assert.equal(BigInt(riscv.getRegistersValuesLong()[5]), 0n)

    console.log(`ok - RV${width}: library members, entry symbol, manual programs, analysis, stdin, lseek, lazy history`)
}
