// Preloaded before a test file: switches the package to its WebAssembly build, and refuses to go on
// if that did not work, so a test never passes on the JavaScript build by accident.
import assert from 'node:assert/strict'

const { useWebAssembly, coreBackend } = await import(new URL('../dist/index.mjs', import.meta.url))
assert.equal(await useWebAssembly(), 'webassembly', 'the WebAssembly build did not load')
assert.equal(coreBackend(), 'webassembly')
