/**
 * The WebAssembly GC build of the Core, loaded on request next to the JavaScript one.
 *
 * TeaVM compiles the same Java to either target. The WebAssembly one runs about twice as fast
 * (Java `long` is a real i64 there instead of an allocated object), but it needs WebAssembly GC
 * and the final exception handling proposal (`exnref`), which only recent engines have, so the
 * JavaScript build stays the default and this module only ever replaces it after the engine has
 * said it can run the other.
 */

/** A module with one struct type: valid when the engine has WebAssembly GC. */
const GC_PROBE = Uint8Array.of(0, 97, 115, 109, 1, 0, 0, 0, 1, 5, 1, 95, 1, 120, 0)

/** A module whose only function holds an empty `try_table`: valid when the engine has `exnref`. */
const EXNREF_PROBE = Uint8Array.of(
    0, 97, 115, 109, 1, 0, 0, 0, // magic, version
    1, 4, 1, 96, 0, 0, // type 0: () -> ()
    3, 2, 1, 0, // one function of type 0
    13, 3, 1, 0, 0, // one tag of type 0
    10, 8, 1, 6, 0, 31, 64, 0, 11, 11 // body: try_table (no catches) end end
)

/** Whether this engine can run the WebAssembly build at all. */
export function engineRunsWasmBuild(): boolean {
    try {
        return (
            typeof WebAssembly === 'object' &&
            WebAssembly.validate(GC_PROBE) &&
            WebAssembly.validate(EXNREF_PROBE)
        )
    } catch {
        return false
    }
}

type NodeProcess = {
    versions?: { node?: string }
    getBuiltinModule?: (name: string) => unknown
}

/**
 * What the runtime's `load` wants as its source: an address in a browser, where it compiles the
 * response as it arrives, and the bytes under Node, whose loader cannot open a `file:` address.
 */
async function sourceOf(url: URL): Promise<string | Uint8Array> {
    const nodeProcess = (globalThis as { process?: NodeProcess }).process
    if (nodeProcess?.versions?.node === undefined) return url.href
    const getBuiltin = nodeProcess.getBuiltinModule
    if (typeof getBuiltin !== 'function') throw new Error('Node cannot read the WebAssembly build')
    const { readFile } = getBuiltin.call(nodeProcess, 'node:fs/promises') as {
        readFile(path: URL): Promise<Uint8Array>
    }
    return new Uint8Array(await readFile(url))
}

type TeaVMGlobal = {
    wasmGC: { load(source: string | Uint8Array): Promise<{ exports: Record<string, unknown> }> }
}

/**
 * Loads the WebAssembly build and returns what the Java side exports, or null when this engine
 * cannot run it or loading failed for any reason: the caller keeps the JavaScript build then.
 */
export async function loadWasmExports(url: URL): Promise<Record<string, unknown> | null> {
    if (!engineRunsWasmBuild()) return null
    try {
        //@ts-ignore The TeaVM runtime is a script that installs `TeaVM.wasmGC` on the global object.
        await import('./wasm-gc-runtime.js')
        const teavm = (globalThis as { TeaVM?: TeaVMGlobal }).TeaVM
        if (!teavm) return null
        const module = await teavm.wasmGC.load(await sourceOf(url))
        return module.exports
    } catch {
        return null
    }
}
