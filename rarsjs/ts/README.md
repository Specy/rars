# RiscV-js
This is a Typescript implementation of a RISC-V simulator made by compiling the [RARS RISC-V simulator](https://github.com/TheThirdOne/rars) to Javascript. 
It is part of a family of javascript assembly interpreters/simulators: 

- MIPS: [git repo](https://github.com/Specy/mars),  [npm package](https://www.npmjs.com/package/@specy/mips)
- RISC-V: [git repo](https://github.com/Specy/rars), [npm package](https://www.npmjs.com/package/@specy/risc-v)
- X86: [git repo](https://github.com/Specy/x86-js), [npm package](https://www.npmjs.com/package/@specy/x86)
- M68K: [git repo](https://github.com/Specy/s68k), [npm package](https://www.npmjs.com/package/@specy/s68k)

## Usage 

First, create an instance of the simulator with `makeRiscVFromFiles`. Supply a virtual source tree and the path of its entry file. Relative `.include` paths resolve from the file containing the directive, while paths beginning with `/` resolve from the virtual root.

Before running the simulator, you must assemble and initialize it. You can then step through the program, simulate with breakpoints, or simulate with a limit.

The optional third factory argument selects the assembly profile:

```typescript
const core = makeRiscVFromFiles(files, 'main.s', { assemblerProfile: 'gnu-compiler-v1' });
```

Omission selects `rars`. `RISCV.assemblerProfiles` lists supported profiles; every present invalid profile throws. GNU compiler v1 provides independent named sections, explicit data alignment, exact eight-byte data, byte-valued string escapes, bounded expressions/aliases, numeric labels, and absolute/PC-relative address fixups. It uses fixed instruction expansions and rejects unsupported directives, ISA attributes, relocations and unresolved runtime helpers. Units link with ld semantics: global and weak symbols (an undefined weak reference is zero), COMDAT groups (the first copy is kept), and `.init_array`/`.fini_array` with ld-provided `__init_array_start`/`__init_array_end` bounds. Global labels enter the global symbol table, so the start label finds them. It does not load or link ELF, compressed/vector instructions, TLS or a standard library. RARS retains its existing educational syntax and macros.

`getAddressOfLabel(name)` returns a defined label/alias address after successful assembly, or `-1` when absent. Profile state is per program; width, memory and execution state retain the existing single-instance constraint.

Floating point literals are read as Java 21 reads them, correctly rounded: `.double` stores
`Double.parseDouble` of its token and `.float` that double rounded to a float, as RARS does, while
the `gnu-compiler-v1` profile reads `.float` straight to single precision, as GNU as does.

⚠️**WARNING**⚠️ You must have only one instance of the simulator at a time. Memory, registers, and other state may be shared or behave unpredictably with multiple instances.

```typescript
import {RISCV, makeRiscVFromFiles, JsRiscV, RegisterName, BackStepAction} from '@specy/risc-v';

const files = {
  'src/main.asm': `
    .include "lib/math.asm"
    .text
    .globl main
  main:
    add_values(t2, 5, 7)
  `,
  'src/lib/math.asm': `
    .macro add_values(%result, %left, %right)
      li %result, %left
      addi %result, %result, %right
    .end_macro
  `,
} as const;

// RISCV.setIs64Bit(true); // Set to 64-bit mode if needed
const riscvSimulator: JsRiscV = makeRiscVFromFiles(files, 'src/main.asm');

riscvSimulator.assemble();
riscvSimulator.initialize(true); // Start at 'main'

while (!riscvSimulator.terminated) {
  await riscvSimulator.step();
}
// or await riscvSimulator.simulate()

const pc = riscvSimulator.programCounter;
const t2 = riscvSimulator.getRegisterValue('t2');

console.log(`Program Counter: ${pc}`);
console.log(`t2: ${t2}`);

// Accessing memory:
const data = riscvSimulator.readMemoryBytes(0xffff0000, 4); // Read 4 bytes from address 0xffff0000
riscvSimulator.setMemoryBytes(0xffff0000, [0x01, 0x02, 0x03, 0x04]); // Write 4 bytes to address 0xffff0000

// Registering Handlers (for syscalls and other events): every print syscall writes its text here.
riscvSimulator.registerHandler("printString", (text: string) => {
  process.stdout.write(text);
});

// Handlers may also be async: returning a promise suspends the simulation until it settles,
// so IO can be backed by an async API without blocking the event loop.
riscvSimulator.registerHandler("readInt", async () => {
  return await promptUserForALine(); // the syscall parses the line itself
});

// Accessing the undo stack:
const undoStack = riscvSimulator.getUndoStack();
undoStack.forEach(step => {
  if (step.action === BackStepAction.REGISTER_RESTORE) {
    console.log(`Register restored at PC ${step.pc}`);
  }
});


// Simulating with breakpoints:
const breakpoints = [0x00400004, 0x00400008]; // Example breakpoint addresses
await riscvSimulator.simulateWithBreakpoints(breakpoints);

//Simulating with a limit
const limit = 100
await riscvSimulator.simulateWithLimit(limit);

//Simulating with breakpoints and a limit
await riscvSimulator.simulateWithBreakpointsAndLimit(breakpoints, limit);

//Setting Register Values:
riscvSimulator.setRegisterValue("t0", 42);
```

## IO handlers

Every syscall that needs to talk to the outside world goes through a handler you register. A
handler can return its result directly, or return a promise of it:

```typescript
riscvSimulator.registerHandler("readInt", () => "42");                // synchronous
riscvSimulator.registerHandler("readString", () => fetchLine());      // asynchronous
```

When a handler returns a promise the simulation suspends at that instruction and resumes once the
promise settles, so nothing has to be shoehorned into a synchronous API. Because of that,
`step`, `simulate`, `simulateWithLimit`, `simulateWithBreakpoints` and
`simulateWithBreakpointsAndLimit` all return a promise. Everything else on `JsRiscV` (registers,
memory, statements, the undo stack) stays synchronous.

If a handler throws, its promise rejects or it answers what its type does not allow, the pending
`step`/`simulate*` call rejects with a `RuntimeError` of kind `handler`, whose `cause` is what the
handler threw or rejected with (see [Running and stopping](#running-and-stopping)).

Synchronous handlers never yield to the event loop: the simulation runs straight through, and the
returned promise settles on a microtask. Overlapping calls are queued and run one after another,
never concurrently.

The syscalls behave as RARS 1.6 does on Java 21, so handlers only move text and bytes:

* Every print syscall formats its value and writes the text through `printString`. Print float
  and print double write what `Float.toString` and `Double.toString` write: `1.0`, `0.001`,
  `1.0E-4`, `1.0E7`, `-0.0`, `NaN`, `Infinity`.
* `readInt`, `readFloat` and `readDouble` answer with the line typed. The syscall trims it and
  parses it as `Integer.parseInt`, `Float.parseFloat` and `Double.parseDouble` do, hexadecimal
  floats and an `f` or `d` suffix included, and stops the program on anything else:
  `Runtime exception at 0x00400004: invalid integer input (syscall 5)`.
* `readChar` answers with the character typed, Enter as `"\n"`. The syscall takes the first
  UTF-16 unit, and an empty answer stops the program with `invalid char input (syscall 12)`.
* `inputDialog` answers `null` when the user cancels, which dialogs 51 to 54 report to the
  program as status -2; `confirm` answers 2 for Cancel. The program waits for `outputDialog` to
  settle.
* Bytes cross as plain arrays of numbers from 0 to 255. `writeFile` answers with the number of
  bytes written, or -1, and the program receives that count; `readFile` and `stdIn` answer
  `[count, bytes]`, with a count of 0 at the end of the input and -1 for a failed read.

```typescript
riscvSimulator.registerHandler("inputDialog", (message) => window.prompt(message)); // null on Cancel
riscvSimulator.registerHandler("writeFile", (descriptor, bytes) => files.write(descriptor, Uint8Array.from(bytes)));
riscvSimulator.registerHandler("stdIn", async (length) => {
  const bytes = await terminal.readBytes(length);
  return [bytes.length, Array.from(bytes)];
});
```

| Handler | Syscalls | Receives | Answers |
| --- | --- | --- | --- |
| `printString` | 1 to 4, 11, 34 to 36 | the text to print | nothing |
| `readInt`, `readFloat`, `readDouble`, `readString` | 5 to 8 | nothing | the line typed |
| `readChar` | 12 | nothing | the character typed |
| `openFile` | 1024 | path, flags, whether to append | the file descriptor, or -1 |
| `readFile` | 63 | file descriptor, most bytes to read | `[count, bytes]` |
| `writeFile` | 64 | file descriptor, bytes | bytes written, or -1 |
| `closeFile` | 57 | file descriptor | nothing |
| `seekFile` | 62 | file descriptor, offset, whence | the new position, or -1 |
| `stdIn` | 63 on descriptor 0 | most bytes to read | `[count, bytes]` |
| `stdOut`, `stdErr` | 64 on descriptors 1 and 2 | bytes | nothing |
| `confirm` | 50 | message | `ConfirmResult` |
| `inputDialog` | 51 to 54 | message | the text entered, or `null` |
| `outputDialog` | 55, 56, 58 to 60 | message, `DialogType` | nothing |
| `sleep` | 32 | milliseconds | nothing |
| `time` | 30, and an instruction reading the `time` CSR | nothing | milliseconds |
| `randomSeed` (optional) | 41 to 44, a generator's first use | the generator's number | a seed from 0 to 2^48 - 1 |

Program time is a handler too, so a run can be given a clock of its own: `sleep` answers syscall 32,
and `time` answers syscall 30 and every read of the `time` CSR. A live run resolves `sleep` on a
timer and returns `Date.now()` from `time`; a scripted run can settle `sleep` immediately, advance a
virtual clock by the requested milliseconds and return that clock instead, which keeps elapsed-time
output reproducible.

## Text

Text is UTF-8 throughout, as in RARS, so a string's text is the same in a literal, through print
string (4) and through write (64), and the same as the UTF-8 a C compiler emits:

* `.ascii`, `.asciz` and `.string` store a literal by code point, `\u` escapes included: four
  hexadecimal digits naming one UTF-16 unit, and two units that make a surrogate pair name one
  character. RARS encodes a literal one UTF-16 unit at a time, which stores a character outside the
  Basic Multilingual Plane as `??`; here `"😀"` and `"\ud83d\ude00"` both store its four bytes.
* Print string (4), the path of open (1024) and the messages of the dialogs (50 to 60) are read
  as UTF-8, the way Java 21 decodes it: a malformed sequence prints as U+FFFD. Message dialog double
  (58), which RARS reads one byte per character, reads UTF-8 like the others.
* Read string (8) and input dialog string (54) store the text as UTF-8, measuring the buffer in
  bytes: a buffer of n bytes holds at most n - 1 bytes of text, so a character can be
  cut where the buffer ends. An unpaired surrogate is stored as `?`, as Java encodes it.
* Print char (11) prints the character numbered by the low byte of `a0`, and read char (12)
  answers the first UTF-16 unit typed: 233 for `é`, 8364 for `€` and 0xd83d, a high surrogate,
  for `😀`.

## Corrections to RARS

* GetCWD (17) writes `/`, the root that relative and absolute file paths both resolve against.
  RARS writes the JVM's working directory, which a browser does not have.
* Input dialog double (53) reads its message from `a0`, as every dialog does; RARS reads `x4`.
* The `time` CSR, and `timeh`, its upper half on RV32, read the program time in milliseconds
  from the `time` handler when an instruction reads them, as the time service (30) does, so a
  scripted run's virtual clock reaches them. RARS writes the host clock into the counter after
  every instruction. The counter reads 0 until the program reads it, and Undo puts back the reading
  before. `cycle` and `instret` count instructions as in RARS.

## Memory observers

A memory-mapped device - a framebuffer, a keyboard register - is modelled by observing the memory
the program reads and writes:

```ts
// Every write in a framebuffer: (address, length, value), with the width of the store in bytes.
const frame = riscvSimulator.addMemoryWriteObserver(0x10010000, 0x10012ffc, (address, length, value) => {
    screen.markDirty(address)
})

// One memory-mapped register: reads and writes, either of which may be null.
const receiver = riscvSimulator.addMemoryAccessObserver(
    0xffff0004,
    () => keyboard.consumeCharacter(),
    null
)

riscvSimulator.removeMemoryObserver(frame)
riscvSimulator.removeMemoryObservers()
```

*   Addresses must be word-aligned, `endAddress` is inclusive and covers its whole word, and a range
    may not cross `0x80000000`; a registration that breaks any of these throws. Either form of a
    high address is accepted: `0xffff0000` and `0xffff0000 | 0` name the same word.
*   Handlers are given signed 32 bit integers, as the guest holds them: the register at
    `0xffff000c` arrives as `-65524`, and a pixel word with its high bit set arrives negative.
    Apply `>>> 0` wherever the unsigned form is wanted.
*   Handlers run synchronously inside the instruction that caused the access, so they must be cheap
    and must not write back into their own range. A returned promise is ignored, unlike an IO
    handler's.
*   An observer is notified *after* the access, with the value the program read or stored. A
    register whose value is consumed by reading it must therefore be reloaded from the handler,
    with `setPeripheralWord`, for the next read.
*   Observers live on the simulator's memory, which assembling and initializing only clear the
    contents of, so a registration survives `assemble()` and `initialize()` and - like a registered
    IO handler - is shared by every `JsRiscV` instance. Notifications start once a program has been
    assembled.
*   `undo()` restores memory through the same stores, so an observed range reports the restored
    values as ordinary writes and a device that follows notifications alone stays in step.

## Random numbers

The random services draw from `java.util.Random`, implemented exactly, so a program that seeds a
generator with ecall 40 prints the numbers RARS prints: `nextInt` (41), `nextInt(bound)` with
Java's rejection of over-represented values (42), `nextFloat` (43) and `nextDouble` (44). Each
number in `a0` names a generator of its own.

A generator the program has not seeded starts, on its first use, from the seed the optional
`randomSeed` handler answers: a whole number from 0 to 2^48 - 1, as `new Random(seed)` takes it.
Without the handler it starts from host randomness, as in RARS; a scripted run answers with a fixed
seed instead and gets the same numbers every time. Handlers are shared by every `JsRiscV` instance,
and registering `undefined` removes one.

```typescript
riscvSimulator.registerHandler("randomSeed", () => 42);      // the same numbers on every run
riscvSimulator.registerHandler("randomSeed", undefined);     // host randomness again
```

The generators belong to the run: `initialize` forgets them, so each starts from a new seed. Undo
puts a generator back as it was before the service ran, so Undo then Step draws the same number.

## Running and stopping

Every run call (`step`, `simulate`, `simulateWithLimit`, `simulateWithBreakpoints`,
`simulateWithBreakpointsAndLimit`) resolves to a `StopReason`, the same enum `@specy/mips` exports:

| `StopReason` | When |
| --- | --- |
| `MAX_STEPS` | the instruction limit was reached; a `step` ends on it |
| `BREAKPOINT` | the program counter reached a breakpoint address, whose instruction has not run, or an `ebreak` ran |
| `NORMAL_TERMINATION` | exit (10) or exit2 (93) ran |
| `CLIFF_TERMINATION` | the program ran off the end of its code; the call that runs the last instruction says so |

`terminated` is read from the program's state, so it is right after `undo()` too: it is true once
an exit has run or there is no statement at the program counter, and `getNextStatement()` then
returns `null`. A program that has exited runs nothing more: every run call resolves to
`NORMAL_TERMINATION` again until `undo()` or `initialize()`. `exitCode` is exit2's `a0` once it has
run and 0 otherwise, after exit and after running off the end too; `initialize` resets it and
undoing the exit puts back the code before it. `getStopReason()` reports the last run call's
reason, `NONE` after `initialize`.

A runtime failure rejects the run call with a `RuntimeError`, an `Error` with typed fields:

```typescript
try {
    await riscvSimulator.simulate();
} catch (error) {
    if (isRuntimeError(error)) {
        // error.kind: 'exception' | 'syscall' | 'handler' | 'internal'
        // error.address, error.sourcePath, error.line (one-based, null without a statement)
        console.log(`${error.sourcePath}:${error.line}: ${error.message}`);
        // main.asm:7: Runtime exception at 0x00400018: invalid or unimplemented syscall service: 99
    }
}
```

`exception` is a RISC-V exception the program had no trap handler for (a misaligned or faulting
access, an illegal instruction, an instruction fetch outside the program), `syscall` a service that
refused its number, arguments or input, `handler` a host handler or memory observer that threw,
rejected or broke its contract (its `cause` is what it threw), and `internal` the simulator
failing. The message is RARS's own. A failure leaves `terminated` false and `getStopReason()` at
`EXCEPTION`; any other rejection, such as a run call on a program that did not assemble, is the
plain error it is.

## Memory layout

Programs run in RARS's memory layout: text from 0x00400000, static data from 0x10010000, the heap
from 0x10040000, the stack down from 0x7fffeffc and memory-mapped IO from 0xffff0000. The data
segment, which static data and the heap share, ends at 0x10400000.

* A RARS-dialect program keeps this layout whatever its size, as in RARS: data past
  0x10040000 shares its addresses with the heap.
* A `gnu-compiler-v1` program's heap follows its static data (`.data`, `.rodata`, `.bss` and
  common symbols): once they reach past 0x10040000, the heap starts at the first 4 KiB page after
  them, where ld and a kernel put the break, and sbrk (9) hands out that address first. Static data
  must fit in the data segment, 4,128,768 bytes from 0x10010000; a program with more fails to
  assemble with `Static data ends at 0x10410000, past the end of the data segment at 0x10400000: ...`.
* Library members linked after a RARS-dialect program must end below 0x10040000, or the program
  fails to assemble with `Static data reaches the heap at 0x10040000`.

`getHeapStart()` reports where the heap starts, and `initialize` empties it again. An allocator
built on sbrk follows the heap wherever it starts.

## API

### `RISCV` Static Class

Provides static methods to initialize and create simulator instances.

* `makeRiscVFromFiles(files: RISCVSourceSet, entryFile: string): JsRiscV`
  Creates a simulator from a snapshot of a virtual source tree and its entry file. Source paths are canonical, root-relative POSIX paths. The factory is also available as `RISCV.makeRiscVFromFiles` and initializes the simulation environment when needed.
* `RISCV.initializeRISCV(): void`
  Initializes the core RISC-V simulation environment. Called internally by `makeRiscVFromFiles`, but can be called explicitly if needed.
* `RISCV.getInstructionSet(): JsInstruction[]`
  Returns an array of objects, each describing a supported RISC-V instruction (name, example, description, tokens).
* `RISCV.setIs64Bit(is64Bit: boolean): void`
  Sets the simulator to operate in 32-bit or 64-bit mode. This affects the instruction set.

### `JsRiscv` Interface

This interface provides methods to control and interact with a RISC-V simulator instance.

#### Properties

* `canUndo: boolean` (Read-only): True if an undo operation can be performed.
* `programCounter: number` (Read-only): The current value of the program counter (PC).
* `stackPointer: number` (Read-only): The current value of the stack pointer (`$sp`).
* `terminated: boolean` (Read-only): Whether the program has ended, by an exit or by running off the end, read from its state so that it is right after `undo()`.
* `exitCode: number` (Read-only): exit2's code once it has run, 0 otherwise.

#### Methods

* `assemble(): RISCVAssembleResult`: Assembles the program. Returns an object containing a report, error list, and an `hasErrors` flag.
* `initialize(startAtMain: boolean): void`: Initializes the simulator state for execution. If `startAtMain` is true, execution begins at the `main` label; otherwise, it starts at the first instruction.
* `step(): Promise<StopReason>`: Executes a single instruction. See [Running and stopping](#running-and-stopping) for every run call's `StopReason` and `RuntimeError`.
* `getStopReason(): StopReason`: Why the last run call stopped, `NONE` after `initialize`.
* `getHeapStart(): number`: Where the heap, and so sbrk's first block, starts: 0x10040000, or the
  first page after static data in a `gnu-compiler-v1` program whose static data reaches past it.
  See [Memory layout](#memory-layout).
* `undo(): void`: Undoes the last instruction executed, if `canUndo` is true and undo is enabled.
* `setUndoEnabled(enabled: boolean): void`: Enables or disables the undo feature.
* `setUndoSize(size: number): void`: Sets the maximum number of steps kept in the undo history. Must be called before assembling.
* `simulateWithLimit(limit: number): Promise<StopReason>`: Simulates the program for a maximum of `limit` instructions. Resolves to the reason the simulation stopped.
* `simulateWithBreakpoints(breakpoints: number[]): Promise<StopReason>`: Simulates the program until a breakpoint is reached or the program terminates. `breakpoints` is an array of memory addresses. Resolves to the reason the simulation stopped.
* `simulateWithBreakpointsAndLimit(breakpoints: number[], limit: number): Promise<StopReason>`: Simulates until a breakpoint, limit is reached, or termination. Resolves to the reason the simulation stopped.
* `getRegisterValue(register: RegisterName): number`: Returns the value of the specified register.
* `setRegisterValue(register: RegisterName, value: number): void`: Sets the value of the specified register.
* `getRegistersValues(): number[]`: Returns an array of all general-purpose register values. The order might be implementation-defined but usually corresponds to register numbers 0-31.
* `getConditionFlags(): number[]`: Gets the 8 condition flags (if applicable, typically related to floating-point or custom extensions).
* `registerHandler<T extends HandlerName>(name: T, handler: (...args: HandlerMap[T]['in']) => HandlerMap[T]['out'] | Promise<HandlerMap[T]['out']>): void`: Registers a handler function for a specific event (e.g., syscalls). See `HandlerName`, `HandlerMap` and [IO handlers](#io-handlers) for details.
* `getUndoStack(): JsBackStep[]`: Returns the undo stack, an array of `JsBackStep` objects representing simulation history.
* `readMemoryBytes(address: number, length: number): number[]`: Reads `length` bytes from memory starting at `address`. Returns an array of byte values. Notifies no memory observer: inspecting memory from the host is not the program reading it.
* `setMemoryBytes(address: number, bytes: number[]): void`: Writes an array of `bytes` to memory starting at `address`, the way the program does: write observers are notified and, while undo is enabled, an undo step is recorded per byte.
* `setPeripheralWord(address: number, value: number): void`: Writes one word-aligned word as a peripheral would, notifying no observer and recording no undo step. See [memory observers](#memory-observers).
* `addMemoryWriteObserver(startAddress: number, endAddress: number, handler): number`: Observes every write in an address range. See [memory observers](#memory-observers).
* `addMemoryAccessObserver(address: number, onRead, onWrite): number`: Observes reads and writes of one word. See [memory observers](#memory-observers).
* `removeMemoryObserver(handle: number): void`: Removes one registration.
* `removeMemoryObservers(): void`: Removes every registration.
* `countMemoryObservers(): number`: The number of live registrations.
* `getNextStatement(): JsProgramStatement | null`: Returns the next `JsProgramStatement` to be executed, or `null` once the program has terminated.
* `getStatementAtAddress(address: number): JsProgramStatement | null`: Gets the program statement at the given memory `address`.
* `getStatementsAtSourceLocation(sourcePath: string, sourceLine: number): JsProgramStatement[]`: Returns every machine statement generated from an original one-based source location, in address order. Pseudo-instructions and macro calls can produce multiple results.
* `getCompiledStatements(): JsProgramStatement[]`: Returns all machine statements after a successful assembly.
* `getParsedStatements(): JsProgramStatement[]`: Returns the parsed statements before pseudo-instruction expansion.
* `getTokenizedLines(): RiscvTokenizedLine[]`: Returns the expanded source stream with each line's path, one-based line number, original source, processed source, and tokens. It is available after tokenization succeeds, including when a later assembly stage fails.
* `getCallStack(): JsRiscvStackFrame[]`: Returns the current call stack as an array of stack frame objects.
* `getLabelAtAddress(address: number): string | null`: Returns the label name at the given memory `address`, or `null` if no label exists there.

#### Helper Functions (for use with `JsRiscv`)

* `registerHandlers(riscv: JsRiscv, handlers: Partial<HandlerMapFns>): void`: A utility to register multiple handlers at once.
* `unimplementedHandler(name: HandlerName): (...args: any[]) => any`: Returns a function that throws an error indicating the handler `name` is not implemented. Useful for stubbing handlers.

#### Types

* `RegisterName`: Union type for RISC-V register names (e.g., `'ra'`, `'sp'`, `'a0'`, `'t0'`, etc.).
* `RISCVSourceSet`: A read-only mapping from canonical virtual source paths to source strings.
* `RISCVSourceLocation`: A source path and one-based source line.
* `HandlerName`: Union type of all possible handler names (keys of `HandlerMap`).
* `StopReason`: Enum of the reasons a run call stops.
* `RuntimeError`: The typed error a run call rejects with when the program fails; `isRuntimeError(error)` tells it apart.
* `HandlerMap`: An object type mapping `HandlerName`s to their expected input argument types (`in`) and return type (`out`). This defines the signature for syscall/event handlers.
* `HandlerMapFns`: An object type where keys are `HandlerName`s and values are the corresponding handler functions.
* `DialogType`: Enum for message dialog types (`PLAIN_MESSAGE`, `ERROR_MESSAGE`, `INFORMATION_MESSAGE`, `WARNING_MESSAGE`, `QUESTION_MESSAGE`).
* `ConfirmResult`: Enum for confirm dialog results (`YES`, `NO`, `CANCEL`).
* `BackStepAction`: Enum representing types of actions that can be undone (e.g., `MEMORY_RESTORE_WORD`, `REGISTER_RESTORE`).
* `JsBackStep`: Interface representing an entry in the undo stack, detailing the action, parameters, and PC value.
* `JsProgramStatement`: Interface representing an assembled instruction, including its source path, source line, address, binary/machine/assembly representations, and full original source line.
* `JsInstructionToken`: Interface representing a token from the assembly source (value, type, and one-based source column).
* `JsInstruction`: Interface describing a RISC-V instruction (name, example, description, token patterns).
* `RiscvTokenizedLine`: An object containing source identity, original and processed line strings, and parsed `JsInstructionToken`s.
* `RISCVAssembleError`: Interface describing an assembly diagnostic with its source path, one-based line and column, warning status, and structured macro expansion trace.
* `RISCVAssembleResult`: Interface for the result of `assemble()`, containing a report string, list of `RISCVAssembleError`s, and a `hasErrors` boolean.
* `JsRiscvStackFrame`: Interface describing a frame on the call stack (PC, target address, SP, FP, register snapshot).

### Dynamic instruction identities

`getCurrentInstructionSerial(): string | null` names the instruction currently executing. Read it
inside a host handler (including an awaited handler) to journal host effects. The eventual
`JsInstructionUndoGroup.serial` and each `JsBackStep.serial` carry exactly that identity. Core-only
services, exits, write-free instructions and failed instructions also receive their own identity.
Pokes have a distinct `JsPokeUndoGroup.serial`; the current-instruction getter remains null in a Poke.
Serials are opaque positive decimal strings, allocated monotonically for the lifetime of this module
across instances, Undo, initialize and reassembly. Do not convert them to JavaScript numbers. At
serial-space exhaustion execution fails rather than reusing an identity.

History keeps only the bounded restore stack. The Undo size still counts raw restore slots, with a
Poke taking one slot; oldest instructions are evicted whole. An instruction larger than the capacity
is discarded whole, so a retained group always represents a complete rollback. `initialize` clears
retained history and Poke journals. Executing an instruction or opening a Poke with recording
disabled clears older retained history, preventing Undo across an unrecorded gap. No-history
instructions still expose a serial inside handlers. Match and retain host frames by serial against
`getUndoGroups`/`getUndoGroupsRange`, never by PC; drop frames whose groups are no longer retained.

`initialize` is rejected while an instruction or Poke is active.
