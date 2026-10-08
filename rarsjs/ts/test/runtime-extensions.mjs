import assert from 'node:assert/strict'
import { RISCV } from '../dist/index.mjs'
const make = source => {
    const core = RISCV.makeRiscVFromFiles({'main.s': source}, 'main.s')
    core.setUndoSize(100)
    assert.equal(core.assemble().hasErrors, false, source)
    core.initialize(true)
    core.setUndoEnabled(true)
    return core
}
// Recoverable failure, followed by a successful allocation; reference service 9 remains unchanged.
{
    const core = make(`.text
main:
li a0, 2147483647
li a7, 1100
ecall
move s0, a0
li a0, 16
li a7, 1100
ecall
li a7, 10
ecall
`.replaceAll('move ', 'mv '))
    await core.simulateWithLimit(100)
    assert.equal(core.getRegisterValue('s0'), -1)
    assert.notEqual(core.getRegisterValue('a0'), -1)
}
// Extended open flags reach the host intact.
{
    const seen = []
    const core = make(`.data
path: .asciz "file.txt"
.text
main:
` + [2,3,10].map(flag => `la a0, path
li a1, ${flag}
li a7, 1024
ecall
`).join('') + `li a7, 10
ecall`)
    core.registerHandler('openFile', (path, flags, append) => { seen.push([flags, append]); return 3 })
    await core.simulateWithLimit(100)
    assert.deepEqual(seen, [[2,false],[3,false],[10,true]])
}
