// Puts the WebAssembly build next to the bundle that loads it: `useWebAssembly` finds it at
// `new URL('./rars.wasm', import.meta.url)`.
import { copyFileSync } from 'node:fs'

copyFileSync(new URL('../src/generated/rars.wasm', import.meta.url), new URL('../dist/rars.wasm', import.meta.url))
