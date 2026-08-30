# RiscV-js
This is a Typescript implementation of a RISC-V simulator made by compiling the [RARS RISC-V simulator](https://github.com/TheThirdOne/rars) to Javascript. 
It is part of a family of javascript assembly interpreters/simulators: 

- MIPS: [git repo](https://github.com/Specy/mars),  [npm package](https://www.npmjs.com/package/@specy/mips)
- RISC-V: [git repo](https://github.com/Specy/rars), [npm package](https://www.npmjs.com/package/@specy/risc-v)
- X86: [git repo](https://github.com/Specy/x86-js), [npm package](https://www.npmjs.com/package/@specy/x86)
- M68K: [git repo](https://github.com/Specy/s68k), [npm package](https://www.npmjs.com/package/@specy/s68k)

## Usage 

First, create an instance of the simulator with the `RISCV.makeRiscvFromSource` function.

Before running the simulator, you must assemble and initialize it. You can then step through the program, simulate with breakpoints, or simulate with a limit.

⚠️**WARNING**⚠️ You must have only one instance of the simulator at a time. Memory, registers, and other state may be shared or behave unpredictably with multiple instances.

```typescript
import {RISCV, JsRiscV, RegisterName, BackStepAction} from '@specy/risc-v';
const sourceCode = `
    li t0, 5          # Load immediate value 5 into register t0
    li t1, 7          # Load immediate value 7 into register t1
    add t2, t0, t1    # Add t0 and t1, store result in t2
`;

// RISCV.setIs64Bit(true); // Set to 64-bit mode if needed
const riscvSimulator: JsRiscV = RISCV.makeRiscVFromSource(sourceCode);

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

// Registering Handlers (for syscalls and other events):
riscvSimulator.registerHandler("printInt", (value: number) => {
  console.log("printInt syscall called with:", value);
});

// Handlers may also be async: returning a promise suspends the simulation until it settles,
// so IO can be backed by an async API without blocking the event loop.
riscvSimulator.registerHandler("readInt", async () => {
  return await promptUserForANumber();
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
riscvSimulator.registerHandler("readInt", () => 42);                  // synchronous
riscvSimulator.registerHandler("readString", () => fetchLine());      // asynchronous
```

When a handler returns a promise the simulation suspends at that instruction and resumes once the
promise settles, so nothing has to be shoehorned into a synchronous API. Because of that,
`step`, `simulate`, `simulateWithLimit`, `simulateWithBreakpoints` and
`simulateWithBreakpointsAndLimit` all return a promise. Everything else on `JsRiscV` (registers,
memory, statements, the undo stack) stays synchronous.

If a handler's promise rejects, the pending `step`/`simulate*` call rejects as well, with the
rejection reason in the message.

Synchronous handlers never yield to the event loop: the simulation runs straight through, and the
returned promise settles on a microtask. Overlapping calls are queued and run one after another,
never concurrently.

## API

### `RISCV` Static Class

Provides static methods to initialize and create simulator instances.

* `RISCV.makeRiscvFromSource(source: string): JsRiscv`
  Creates a new `JsRiscv` instance from RISC-V assembly source code. This also initializes the underlying RISC-V simulation environment if it hasn't been already.
* `RISCV.initializeRISCV(): void`
  Initializes the core RISC-V simulation environment. Called internally by `makeRiscvFromSource`, but can be called explicitly if needed.
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
* `terminated: boolean` (Read-only): True if the simulation has terminated (e.g., via an exit syscall or error).

#### Methods

* `assemble(): RISCVAssembleResult`: Assembles the program. Returns an object containing a report, error list, and an `hasErrors` flag.
* `initialize(startAtMain: boolean): void`: Initializes the simulator state for execution. If `startAtMain` is true, execution begins at the `main` label; otherwise, it starts at the first instruction.
* `step(): Promise<StopReason>`: Executes a single instruction. Resolves to the reason the simulation stopped.
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
* `readMemoryBytes(address: number, length: number): number[]`: Reads `length` bytes from memory starting at `address`. Returns an array of byte values.
* `setMemoryBytes(address: number, bytes: number[]): void`: Writes an array of `bytes` to memory starting at `address`.
* `getCurrentStatementIndex(): number`: Returns the index of the current instruction in the list of assembled program statements.
* `getNextStatement(): JsProgramStatement | null`: Returns the next `JsProgramStatement` to be executed, or `null` if at the end.
* `getStatementAtAddress(address: number): JsProgramStatement | null`: Gets the program statement at the given memory `address`.
* `getStatementAtSourceLine(line: number): JsProgramStatement | null`: Gets the program statement corresponding to the original source `line` number.
* `getCompiledStatements(): JsProgramStatement[]`: Returns an array of all assembled program statements.
* `getParsedStatements(): JsInstructionToken[]`: Returns an array of tokens from the initial parsing stage.
* `getTokenizedLines(): RiscvTokenizedLine[]`: Returns an array of lines, each with its source string and corresponding tokens.
* `getCallStack(): JsRiscvStackFrame[]`: Returns the current call stack as an array of stack frame objects.
* `getLabelAtAddress(address: number): string | null`: Returns the label name at the given memory `address`, or `null` if no label exists there.

#### Helper Functions (for use with `JsRiscv`)

* `registerHandlers(riscv: JsRiscv, handlers: Partial<HandlerMapFns>): void`: A utility to register multiple handlers at once.
* `unimplementedHandler(name: HandlerName): (...args: any[]) => any`: Returns a function that throws an error indicating the handler `name` is not implemented. Useful for stubbing handlers.

#### Types

* `RegisterName`: Union type for RISC-V register names (e.g., `'ra'`, `'sp'`, `'a0'`, `'t0'`, etc.).
* `HandlerName`: Union type of all possible handler names (keys of `HandlerMap`).
* `HandlerMap`: An object type mapping `HandlerName`s to their expected input argument types (`in`) and return type (`out`). This defines the signature for syscall/event handlers.
* `HandlerMapFns`: An object type where keys are `HandlerName`s and values are the corresponding handler functions.
* `DialogType`: Enum for message dialog types (`ERROR_MESSAGE`, `INFORMATION_MESSAGE`, `WARNING_MESSAGE`, `QUESTION_MESSAGE`).
* `ConfirmResult`: Enum for confirm dialog results (`YES`, `NO`, `CANCEL`).
* `BackStepAction`: Enum representing types of actions that can be undone (e.g., `MEMORY_RESTORE_WORD`, `REGISTER_RESTORE`).
* `JsBackStep`: Interface representing an entry in the undo stack, detailing the action, parameters, and PC value.
* `JsProgramStatement`: Interface representing an assembled instruction, including source line, address, binary/machine/assembly representations.
* `JsInstructionToken`: Interface representing a token from the assembly source (value, type, source position).
* `JsInstruction`: Interface describing a RISC-V instruction (name, example, description, token patterns).
* `RiscvTokenizedLine`: An object containing the original line string and its parsed `JsInstructionToken`s.
* `RISCVAssembleError`: Interface describing an assembly error (message, line/column, warning status, etc.).
* `RISCVAssembleResult`: Interface for the result of `assemble()`, containing a report string, list of `RISCVAssembleError`s, and a `hasErrors` boolean.
* `JsRiscvStackFrame`: Interface describing a frame on the call stack (PC, target address, SP, FP, register snapshot).