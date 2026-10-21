// Runs every test file of this package on the WebAssembly build instead of the JavaScript one.
// Node needs --experimental-wasm-exnref to compile that build (see `npm run test:wasm`); a file that
// imports dist/index.mjs afterwards gets the module this script has already switched.
import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'

const scripts = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8')).scripts
const files = [...scripts.test.matchAll(/node (test\/[\w-]+\.mjs)/g)].map(match => match[1])
let failed = 0
for (const file of files) {
    const result = spawnSync(process.execPath, ['--experimental-wasm-exnref', '--import', './test/use-wasm.mjs', file], {
        stdio: 'inherit',
        cwd: new URL('..', import.meta.url),
    })
    if (result.status !== 0) {
        failed++
        console.error(`FAILED on WebAssembly: ${file}`)
    }
}
process.exit(failed === 0 ? 0 : 1)
