//@ts-ignore
import {makeRiscVFromFiles as _makeRiscVFromFiles, initializeRISCV as _initializeRISCV, getInstructionSet as _getInstructionSet, setIs64Bit as _setIs64Bit, is64Bit as _is64Bit} from './generated/rars'


export type JsInstructionToken = {
    /** One-based column in the processed source line. */
    sourceColumn: number;
    value: string;
    type: string
}

export enum StopReason {
    NONE = -1,         // no simulation has run yet
    BREAKPOINT,
    EXCEPTION,
    MAX_STEPS,         // includes step mode (where maxSteps is 1)
    NORMAL_TERMINATION,
    CLIFF_TERMINATION, // run off bottom of program
    PAUSE,
    STOP
}



/*
public abstract int openFile(String filename, int flags, boolean append) throws RISCVIOError;
    public abstract void closeFile(int fileDescriptor) throws RISCVIOError;
    public abstract void writeFile(int fileDescriptor, byte[] buffer) throws RISCVIOError;
    public abstract int readFile(int fileDescriptor, byte[] destination, int length) throws RISCVIOError;


    // 0 ---> meaning Yes
    // 1 ---> meaning No
    // 2 ---> meaning Cancel
    public abstract int confirm(String message);

    public abstract String inputDialog(String message);

     *  ERROR_MESSAGE = 0
     *  INFORMATION_MESSAGE = 1
     *  WARNING_MESSAGE = 2
     *  QUESTION_MESSAGE = 3
public abstract void outputDialog(String message, int type);

public abstract double askDouble(String message);

public abstract float askFloat(String message);

public abstract int askInt(String message);

public abstract String askString(String message);

public abstract double readDouble();

public abstract float readFloat();

public abstract int readInt();

public abstract String readString();

public abstract char readChar();

public abstract void logLine(String message);

public abstract void log(String message);

public abstract void printChar(char c);

public abstract void printDouble(double d);

public abstract void printFloat(float f);

public abstract void printInt(int i);

public abstract void printString(String l);


public abstract void sleep(int milliseconds);

public abstract double time();

public abstract void stdIn(byte[] buffer, int length);

public abstract void stdOut(byte[] buffer);

public abstract void stdErr(byte[] buffer);
 */

export enum DialogType {
    ERROR_MESSAGE = 0,
    INFORMATION_MESSAGE = 1,
    WARNING_MESSAGE = 2,
    QUESTION_MESSAGE = 3
}

export enum ConfirmResult {
    YES = 0,
    NO = 1,
    CANCEL = 2
}

export type HandlerMap = {
    openFile: {in: [filename: string, flags: number, append: boolean], out: number}
    closeFile: {in: [fileDescriptor: number], out: void}
    writeFile: {in: [fileDescriptor: number, buffer: number[]], out: void}
    readFile: {in: [fileDescriptor: number, destination: number[], length: number], out: [readOrEof: number, buffer: number[]]}
    confirm: {in: [message: string], out: ConfirmResult}
    inputDialog: {in: [message: string], out: string}
    outputDialog: {in: [message: string, type: DialogType], out: void}
    askDouble: {in: [message: string], out: number}
    askFloat: {in: [message: string], out: number}
    askInt: {in: [message: string], out: number}
    askString: {in: [message: string], out: string}
    readDouble: {in: [], out: number}
    readFloat: {in: [], out: number}
    readInt: {in: [], out: number}
    readString: {in: [], out: string}
    readChar: {in: [], out: string}
    logLine: {in: [message: string], out: void}
    log: {in: [message: string], out: void}
    printChar: {in: [c: string], out: void}
    printDouble: {in: [d: number], out: void}
    printFloat: {in: [f: number], out: void}
    printInt: {in: [i: number], out: void}
    printString: {in: [l: string], out: void}
    /**
     * Syscall 32: the program asks to be suspended for this many milliseconds. Return a promise
     * that settles when the wait is over to suspend the simulation without blocking the host.
     */
    sleep: {in: [milliseconds: number], out: void}
    /**
     * Syscall 30: the program time in milliseconds, split by the syscall into a0 (low word) and
     * a1 (high word). Answer with `Date.now()` for a live run, or with a virtual clock for a
     * scripted one, so that elapsed-time output stays reproducible.
     */
    time: {in: [], out: number}
    stdIn: {in: [buffer: number[], length: number], out: void}
    stdOut: {in: [buffer: number[]], out: void}
    stdErr: {in: [buffer: number[]], out: void}
}

/**
 * Notified after a write anywhere in an observed address range.
 *
 * `length` is the width of the access in bytes (4, 2 or 1) and `value` is what the program stored,
 * so a byte or halfword store reports only the bytes it touched; a peripheral that mirrors whole
 * words should re-read the containing word with `readMemoryBytes` rather than trust `value`.
 *
 * Both `address` and `value` are signed 32 bit integers, as the guest holds them: the register at
 * `0xffff000c` arrives as `-65524`, and a pixel word with its high bit set arrives negative too.
 * Apply `>>> 0` wherever the unsigned form is wanted.
 */
export type MemoryWriteObserver = (address: number, length: number, value: number) => void

/**
 * Notified after a read or a write of one observed word, with the same signed 32 bit numbers as
 * `MemoryWriteObserver`. `value` is the value the program read or stored; on a read it is what
 * memory held *before* the observer ran, so a register whose value is consumed by reading it must
 * be reloaded from the handler for the next read.
 */
export type MemoryAccessObserver = (address: number, value: number) => void

/** Identifies one registration, for `removeMemoryObserver`. */
export type MemoryObserverHandle = number

export type JsInstruction = {
    name: string;
    example: string;
    description: string;
    tokens: JsInstructionToken[];
    getIsRv64Only: () => boolean;
}

export type RiscvTokenizedLine = {
    sourcePath: string;
    /** One-based line in `sourcePath`. */
    sourceLine: number;
    /** Exact line supplied in the source set. */
    source: string;
    /** Line after assembler substitutions such as `.eqv`. */
    processedSource: string;
    tokens: JsInstructionToken[]
}

export type RISCVSourceLocation = {
    sourcePath: string
    /** One-based line in `sourcePath`. */
    sourceLine: number
}

export type RISCVSourceSet = Readonly<Record<string, string>>

export type RISCVAssembleError = {
    isWarning: boolean
    message: string
    macroExpansionTrace: RISCVSourceLocation[]
    sourcePath: string
    /** One-based line in `sourcePath`. */
    sourceLine: number
    /** One-based column in `sourcePath`. Zero only for diagnostics without a source location. */
    sourceColumn: number
}

export type RISCVAssembleResult = {
    report: string
    errors: RISCVAssembleError[]
    /** True only when at least one diagnostic is a real error; warnings alone leave the assembled program runnable. */
    hasErrors: boolean
    /** True when at least one diagnostic is a warning. */
    hasWarnings: boolean
}


export class RISCV {
    public static makeRiscVFromFiles = makeRiscVFromFiles
    public static initializeRISCV = initializeRISCV
    public static getInstructionSet(){
        return _getInstructionSet() as JsInstruction[]
    }
    public static setIs64Bit(is64Bit: boolean) {
        RISCV.initializeRISCV()
        _setIs64Bit(is64Bit)
    }

    public static is64Bit(): boolean {
        RISCV.initializeRISCV()
        return _is64Bit()
    }
}

export type JsRiscVStackFrame = {
    /**
     * The program counter value at the moment the stack frame was created.
     */
    pc: number;
    /**
     * The address of the target instruction.
     */
    toAddress: number;
    /**
     * The stack pointer value at the moment the stack frame was created.
     */
    sp: number;
    /**
     * The frame pointer value at the moment the stack frame was created.
     */
    fp: number;
    /**
     * The values of all registers at the moment the stack frame was created.
     */
    registers: number[];
}

/**
 * Represents a statement in the assembled program.
 */
export interface JsProgramStatement {
    /** Canonical path of the original source file. */
    readonly sourcePath: string;
    /** The one-based line number in the original source file. */
    readonly sourceLine: number;
    /**
     * The memory address of the instruction.
     */
    readonly address: number;
    /**
     * The binary representation of the instruction.
     */
    readonly binaryStatement: number;
    /**
     * The original source code line.
     */
    readonly source: string;
    /**
     * The machine code representation of the instruction.
     */
    readonly machineStatement: string;


    /**
     * The assembly representation of the instruction.
     */
    readonly assemblyStatement: string;
}

/**
 * Enum representing the types of "undo" actions.
 */
export enum BackStepAction {
    MEMORY_RESTORE_RAW_WORD,
    MEMORY_RESTORE_DOUBLE_WORD,
    MEMORY_RESTORE_WORD,
    MEMORY_RESTORE_HALF,
    MEMORY_RESTORE_BYTE,
    REGISTER_RESTORE,
    PC_RESTORE,
    CONTROL_AND_STATUS_REGISTER_RESTORE,
    CONTROL_AND_STATUS_REGISTER_BACKDOOR,
    FLOATING_POINT_REGISTER_RESTORE,
    DO_NOTHING,
    /**
     * Undoes one instruction's worth of the `cycle` and `instret` counters. The simulator adds one
     * to each of them per instruction and nothing else writes them, so a single entry without
     * parameters covers both.
     *
     * Members mirror the core's `BackStepper.Action` by position, because the core reports an
     * action as that enum's ordinal. Append here; never insert.
     */
    CONTROL_AND_STATUS_COUNTERS_DECREMENT,
    /**
     * Restores one control and status register the way the host's setter wrote it, through the
     * register's own value: a linked register writes the register it aliases, a read only counter
     * is written anyway. Only a Poke records this, and only inside its own entry.
     */
    CONTROL_AND_STATUS_REGISTER_POKE_RESTORE,
    /**
     * A whole Poke: every value one `beginPoke`/`endPoke` transaction wrote, restored together.
     * A Poke is a single back step, so it takes one slot of the undo size whatever it wrote.
     */
    POKE
}

/**
 * Represents a back step in the simulation undo stack.
 */
export interface JsBackStep {
    /**
     * The action performed (e.g., register write, memory write).
     */
    readonly action: BackStepAction;

    /**
     * Information about the action
     */
    readonly param1: number;

    /**
     * Information about the action
     */
    readonly param2: number;
    /**
     * The program counter value before the action, or -1 for an action that belongs to no
     * instruction: a Poke, or a host write made before anything ran.
     */
    readonly pc: number;

    /**
     * Whether this step is a whole Poke rather than one effect of an instruction. It is the
     * discriminator this list needs, since a Poke and a pre-run host write both carry `pc` -1.
     */
    readonly isPoke: boolean;
}

/**
 * One value a Poke changed: a register, named the way this package's getters spell it, or a run of
 * consecutive memory bytes. `old` is what the simulator held when the write happened and `new` what
 * it held when the Poke was closed, so a value written twice inside one Poke reports its final
 * state.
 */
export type JsPokeWrite = JsPokeRegisterWrite | JsPokeMemoryWrite

/**
 * A register a Poke wrote. `name` is the register's own name, as this package spells it: a key of
 * `RISCVRegisters` (`t0`), an entry of `RISCV_FLOATING_POINT_REGISTERS` (`ft0`) or one of
 * `RISCV_CSR_REGISTERS` (`fcsr`) - the file it belongs to is the file that name is in.
 *
 * Every register here is 64 bits wide, which no JS number holds, so both values are **signed
 * decimal strings**, the shape `getRegisterValueLong` and `getRegistersValuesLong` already use:
 * read them with `BigInt(write.old)`, and with `BigInt.asUintN(64, BigInt(write.old))` for the
 * unsigned form that `highLowToBigint` returns for the same register.
 */
export type JsPokeRegisterWrite = {
    readonly type: 'register'
    readonly name: string
    readonly old: string
    readonly new: string
}

/**
 * A run of consecutive memory bytes a Poke wrote, `address` being the first one, unsigned, and the
 * two arrays holding one byte (0 to 255) per address. A Poke that writes addresses that are not
 * adjacent reports one of these per run, by ascending address. Unlike `readMemoryBytes`, these are
 * ordinary JS arrays.
 */
export type JsPokeMemoryWrite = {
    readonly type: 'memory'
    readonly address: number
    readonly old: number[]
    readonly new: number[]
}

/**
 * One entry of the undo history: everything a single `undo()` reverts, which is either one executed
 * instruction or one Poke. Entries, their back steps and their writes are ordinary objects with own
 * properties, so a whole history can be cloned, serialized or deep-compared as it comes.
 */
export type JsUndoGroup = JsInstructionUndoGroup | JsPokeUndoGroup

/**
 * One executed instruction, at the address `pc`, with every back step it recorded: the values it
 * overwrote, and the `cycle`/`instret` decrement every instruction pushes on top of them.
 */
export type JsInstructionUndoGroup = {
    readonly kind: 'instruction'
    readonly pc: number
    /** The instruction's back steps, newest first. */
    readonly steps: JsBackStep[]
    /** Always empty: only a Poke reports writes. */
    readonly writes: readonly []
}

/**
 * One Poke: register or memory values written by the host between two instructions, recorded as a
 * step of its own. It belongs to no instruction, so `pc` is -1 and undoing it restores exactly what
 * `writes` lists, leaving the program counter, the counters, the call stack and everything else
 * alone.
 */
export type JsPokeUndoGroup = {
    readonly kind: 'poke'
    readonly pc: -1
    /** The single back step the Poke is: one slot of the history, whatever the Poke wrote. */
    readonly steps: [JsBackStep]
    readonly writes: JsPokeWrite[]
}


export enum RISCVRegisters {
    zero = 0,
    ra = 1,
    sp = 2,
    gp = 3,
    tp = 4,
    t0 = 5,
    t1 = 6,
    t2 = 7,
    s0 = 8,
    s1 = 9,
    a0 = 10,
    a1 = 11,
    a2 = 12,
    a3 = 13,
    a4 = 14,
    a5 = 15,
    a6 = 16,
    a7 = 17,
    s2 = 18,
    s3 = 19,
    s4 = 20,
    s5 = 21,
    s6 = 22,
    s7 = 23,
    s8 = 24,
    s9 = 25,
    s10 = 26,
    s11 = 27,
    t3 = 28,
    t4 = 29,
    t5 = 30,
    t6 = 31,
}

export type RegisterName = keyof typeof RISCVRegisters

function keysOfEnum<T>(e: T): string[]{
    //@ts-ignore
    return Object.keys(e).filter(k => isNaN(Number(k)))
}


export const RISCV_REGISTERS = keysOfEnum(RISCVRegisters) as RegisterName[]

/**
 * The floating point registers, in the order `getFloatingPointRegistersValues` returns them, which
 * is the order the core holds them in: register number 0 to 31, spelled as RARS spells them.
 */
export const RISCV_FLOATING_POINT_REGISTERS = [
    'ft0', 'ft1', 'ft2', 'ft3', 'ft4', 'ft5', 'ft6', 'ft7',
    'fs0', 'fs1',
    'fa0', 'fa1', 'fa2', 'fa3', 'fa4', 'fa5', 'fa6', 'fa7',
    'fs2', 'fs3', 'fs4', 'fs5', 'fs6', 'fs7', 'fs8', 'fs9', 'fs10', 'fs11',
    'ft8', 'ft9', 'ft10', 'ft11',
] as const

export type FloatingPointRegisterName = typeof RISCV_FLOATING_POINT_REGISTERS[number]

/**
 * The control and status registers the core implements, in the order
 * `getControlAndStatusRegistersValues` returns them. `fflags` and `frm` are windows onto `fcsr`,
 * and `cycleh`, `timeh` and `instreth` are the high halves of `cycle`, `time` and `instret`, so
 * those pairs always agree.
 */
export const RISCV_CSR_REGISTERS = [
    'ustatus', 'fflags', 'frm', 'fcsr', 'uie', 'utvec', 'uscratch', 'uepc', 'ucause', 'utval',
    'uip', 'cycle', 'time', 'instret', 'cycleh', 'timeh', 'instreth',
] as const

export type CsrRegisterName = typeof RISCV_CSR_REGISTERS[number]


type HandlerName = keyof HandlerMap


/**
 * A handler may return its result directly, or a promise of it. When a handler returns a promise
 * the simulation suspends until it settles, so IO can be backed by an async API (prompting the
 * user, reading a file, awaiting a worker) without blocking the event loop. If the promise
 * rejects, the pending `step`/`simulate*` call rejects too.
 */
export type HandlerMapFns = {
    [K in HandlerName]: (...args: HandlerMap[K]['in']) => HandlerMap[K]['out'] | Promise<HandlerMap[K]['out']>
}

export function registerHandlers(riscv: JsRiscV, handlers: HandlerMapFns) {
    for (const [name, handler] of Object.entries(handlers)) {
        riscv.registerHandler(name as HandlerName, handler as (...args: HandlerMap[HandlerName]['in']) => HandlerMap[HandlerName]['out'] | Promise<HandlerMap[HandlerName]['out']>)
    }
}

export function unimplementedHandler(name: HandlerName) {
    return function () {
        throw new Error(`Handler ${name} is not implemented`)
    }
}

/**
 * Interface for interacting with a RISCV simulator.
 */
export interface JsRiscV {
    /**
     * Assembles the program.
     */
    assemble(): RISCVAssembleResult;

    /**
     * Initializes the simulator.
     * @param startAtMain If true, starts execution at the 'main' label. Otherwise, starts at the first instruction.
     */
    initialize(startAtMain: boolean): void;



    /**
     * Gets the current stop reason.
     */
    getStopReason(): StopReason;

    /**
     * Executes a single instruction.
     *
     * The promise settles on a microtask unless an IO handler returned a promise, in which case
     * it settles once that handler and the rest of the instruction have finished.
     */
    step(): Promise<StopReason>;



    /**
     * Sets the size of the undo stack, must be called before assembling the program.
     * @param size
     */
    setUndoSize(size: number): void;

    /**
     * Undoes the newest entry of the history: the last instruction executed, or the last Poke made,
     * whichever is on top. Undoing a Poke restores every value it wrote and touches nothing else.
     */
    undo(): void;


    /**
     * Gets the statement at the given address.
     * @param address
     */
    getStatementAtAddress(address: number): JsProgramStatement;

    /** Gets every machine statement generated from an original source location. */
    getStatementsAtSourceLocation(sourcePath: string, sourceLine: number): JsProgramStatement[];


    getTokenizedLines(): RiscvTokenizedLine[]
    /**
     * Checks if the simulation can be undone.
     * @returns True if the simulation can be undone, false otherwise.
     * */
    canUndo: boolean;



    /**
     * Gets the call stack.
     * @returns An array of memory addresses representing the call stack.
     */
    getCallStack(): JsRiscVStackFrame[]



    /**
     * Gets the compiled statements.
     * @returns An array of `JsProgramStatement` objects representing the compiled program.
     */
    getCompiledStatements(): JsProgramStatement[]


    getParsedStatements(): JsProgramStatement[]


    /**
     * Gets the label at the given address.
     * @param address The memory address.
     * @returns The label at the given address, or null if no label is found.
     */
    getLabelAtAddress(address: number): string | null

    /**
     * Sets whether the undo feature is enabled.
     * @param enabled True to enable the undo feature, false to disable it.
     */
    setUndoEnabled(enabled: boolean): void;

    /**
     * Simulates until the stop condition is met. it might be a breakpoint, exception, etc...
     */
    simulate(): Promise<StopReason>;

    /**
     * Simulates the program for a limited number of instructions.
     * @param limit The maximum number of instructions to execute.
     * @returns A promise resolving to the reason the simulation stopped.
     */
    simulateWithLimit(limit: number): Promise<StopReason>;

    /**
     * Simulates the program until a breakpoint is reached.
     * @param breakpoints An array of memory addresses where the simulation should pause.
     * @returns A promise resolving to the reason the simulation stopped.
     */
    simulateWithBreakpoints(breakpoints: number[]): Promise<StopReason>;

    /**
     * Simulates the program with both breakpoints and a limit.
     * @param breakpoints An array of memory addresses where the simulation should pause.
     * @param limit The maximum number of instructions to execute.
     * @returns A promise resolving to the reason the simulation stopped.
     */
    simulateWithBreakpointsAndLimit(breakpoints: number[], limit: number): Promise<StopReason>;

    /**
     * Gets the value of a register.
     * @param register The name of the register.
     * @returns The value of the register.
     */
    getRegisterValue(register: RegisterName): number;

    /**
     * Gets the value of a register in long format.
     * @param register The name of the register.
     * @returns The value of the register in long format.
     */
    getRegisterValueLong(register: RegisterName): string;

    /**
     * Registers a handler function for a specific event or condition.
     * @param name The name of the event or condition.
     * @param handler The handler function to be called when the event occurs. The function signature depends on the event name.
     */
    registerHandler<T extends HandlerName>(name: T, handler: (...args: HandlerMap[T]['in']) => HandlerMap[T]['out'] | Promise<HandlerMap[T]['out']>): void;

    /**
     * Gets the current value of the stack pointer.
     * @returns The value of the stack pointer.
     */
    stackPointer: number;


    /**
     * Gets the current value of the stack pointer in long format.
     * @returns The value of the stack pointer in long format.
     */
    stackPointerLong: string;
    /**
     * Gets the current value of the program counter.
     * @returns The value of the program counter.
     */
    programCounter: number;

    /**
     * Gets the current value of the program counter in long format.
     * @returns The value of the program counter in long format.
     */
    programCounterLong: string;

    /**
     * Gets the values of all registers.
     * @returns An array containing the register values. The order of the values is implementation defined.
     */
    getRegistersValues(): number[];


    /**
     * Gets the values of all registers in long format.
     * @returns An array containing the register values in long format. The order of the values is implementation defined.
     */
    getRegistersValuesLong(): string[];

    /**
     * Gets every floating point register as a high/low pair of 32 bit halves: element `2 * i` is
     * the high half of register `i` and `2 * i + 1` its low half, so the array holds 64 numbers.
     * The registers are in `RISCV_FLOATING_POINT_REGISTERS` order, and both halves matter on both
     * targets: the file is 64 bit wide even on RV32, where a single is NaN-boxed into it (its high
     * half is `0xFFFFFFFF`).
     *
     * The core hands this over as an `Int32Array` of signed 32 bit halves, so a half with its top
     * bit set reads back negative: compose each pair with `highLowToBigint`, which takes both
     * halves unsigned, rather than mapping `>>> 0` over the array (a typed array's `map` truncates
     * the result back to int32, handing you the negative value again).
     *
     * Pairs rather than decimal strings because this is read on every panel refresh, and a 64 bit
     * conversion per register is the kind of work that costs in the compiled core.
     */
    getFloatingPointRegistersValues(): Int32Array;

    /**
     * Sets one floating point register. Outside a Poke the write is direct: no undo entry is
     * recorded, because presetting a register from the host is not something the program did.
     * Inside a Poke it joins the open transaction. Split the value with `bigintToHighLow`, and
     * NaN-box a single yourself (`0xFFFFFFFFn << 32n | bits`) if that is what you mean.
     * @param index Position in `RISCV_FLOATING_POINT_REGISTERS`. Must be a whole number from 0 to
     * 31; anything else throws.
     */
    setFloatingPointRegisterValue(index: number, high: number, low: number): void;

    /**
     * Gets every control and status register as a high/low pair, in the same shape as
     * `getFloatingPointRegistersValues`: an `Int32Array` of 34 signed halves for the 17 registers
     * of `RISCV_CSR_REGISTERS`, to be composed with `highLowToBigint` for the same reason. The
     * `cycle` and `instret` counters are settled first, so they count every instruction executed
     * so far.
     */
    getControlAndStatusRegistersValues(): Int32Array;

    /**
     * Sets one control and status register. Outside a Poke the write is direct and records no undo
     * entry; inside a Poke it joins the open transaction. Writing `fflags` or `frm` updates `fcsr`,
     * and writing a counter such as `cycle` is allowed here even though the program cannot write
     * it - so a counter is pokeable, and undoing that Poke puts the counter back.
     * @param index Position in `RISCV_CSR_REGISTERS`. Must be a whole number from 0 to 16;
     * anything else throws.
     */
    setControlAndStatusRegisterValue(index: number, high: number, low: number): void;

    /**
     * Gets the undo stack, one element per back step, newest first. An instruction usually occupies
     * several of them - it records the values it overwrote and the counter decrement - while a Poke
     * is exactly one, the element with `isPoke` set, whatever it wrote. Use `getUndoGroups` to read
     * the history the way `undo()` pops it, one entry per instruction or Poke.
     * @returns An array of `JsBackStep` objects representing the history of the simulation.
     */
    getUndoStack(): JsBackStep[];

    /**
     * Gets the undo history grouped the way `undo()` pops it: one entry per executed instruction or
     * per Poke, newest first, each carrying the back steps it is made of. A Poke entry also carries
     * what it changed, with the old and the new value of each write.
     */
    getUndoGroups(): JsUndoGroup[];

    /**
     * Opens a Poke: a register or memory value changed by the host between two instructions,
     * recorded in this history as a step of its own.
     *
     * Until `endPoke` the setters - `setRegisterValue`, `setFloatingPointRegisterValue`,
     * `setControlAndStatusRegisterValue` and `setMemoryBytes` - journal what they write into the
     * open transaction, however many of them are called, and `endPoke` records the lot as one
     * history entry that one `undo()` reverts and that takes one slot of the undo size, whether it
     * wrote one byte or a hundred. Outside a transaction the same setters stay direct and record
     * nothing, which is what presetting state needs.
     *
     * Throws if a Poke is already open, or if a `step`/`simulate*` call is still in flight.
     */
    beginPoke(): void;

    /**
     * Closes the open Poke.
     * @returns True if it recorded one history entry, false if nothing changed - or if undo is
     * disabled or its size is 0, in which case the writes stand but cannot be undone.
     * Throws if no Poke is open.
     */
    endPoke(): boolean;

    /** Whether a Poke is open, so that the setters journal rather than writing straight through. */
    pokeOpen(): boolean;

    /**
     * Reads a sequence of bytes from memory.
     *
     * Reading through this method notifies no memory observer: inspecting memory from the host is
     * not the program reading it, so a memory viewer never drives a memory-mapped register.
     * @param address The starting memory address.
     * @param length The number of bytes to read.
     * @returns An array of bytes read from memory.
     */
    readMemoryBytes(address: number, length: number): number[];

    /**
     * Writes a sequence of bytes to memory.
     *
     * Unlike `readMemoryBytes`, this writes the way the program does, so write observers are
     * notified and a memory mapped display repaints. It records no undo step of its own: a host
     * write is not an instruction, and recording it would make the next `undo()` revert it together
     * with the instruction that ran before it. Inside a Poke the same write joins the open
     * transaction instead, and a byte already holding the value written is skipped entirely.
     *
     * Use `setPeripheralWord` for a device keeping its own register up to date.
     * @param address The starting memory address.
     * @param bytes An array of bytes to write to memory.
     */
    setMemoryBytes(address: number, bytes: number[]): void;

    /**
     * Writes one word as a peripheral would: no observer is notified and no undo step is recorded,
     * because the write is not the program acting. This is how a device model refreshes a
     * memory-mapped register - a ready bit, a pending character - without feeding its own observer
     * or consuming undo history.
     * @param address The word address, which must be word-aligned. Either form of a high address
     * is accepted: `0xffff0000` and `0xffff0000 | 0` name the same word.
     * @param value The 32 bit value to store, raw, without byte-order adjustment.
     */
    setPeripheralWord(address: number, value: number): void;

    /**
     * Observes every write in an address range, the shape a framebuffer wants.
     *
     * Both addresses must be word-aligned, `endAddress` is inclusive and covers its whole word, and
     * the range may not cross 0x80000000 (split it in two registrations instead); a range that
     * breaks any of these throws. Either form of a high address is accepted: `0xffff0000` and
     * `0xffff0000 | 0` name the same word. The handler runs synchronously inside the storing instruction, so
     * it must be cheap and must not write back into its own range; a returned promise is ignored.
     *
     * Observers live on the simulator's memory, which assembling and initializing only clear the
     * contents of, so a registration survives `assemble()` and `initialize()`. For the same reason
     * it is shared by every `JsRiscV` instance: register once per page, or remove the previous
     * registration before registering again for a newly built program.
     *
     * Notifications only start once a program has been assembled, and undo notifies too: restoring
     * memory during `undo()` goes through the same stores, so an observed range reports the
     * restored values as ordinary writes.
     * @returns A handle for `removeMemoryObserver`.
     */
    addMemoryWriteObserver(startAddress: number, endAddress: number, handler: MemoryWriteObserver): MemoryObserverHandle;

    /**
     * Observes reads and writes of a single word, the shape a memory-mapped register wants. The
     * address must be word-aligned. Pass `null` for a direction you do not care about. The same
     * lifetime and synchronous-handler rules as `addMemoryWriteObserver` apply.
     * @returns A handle for `removeMemoryObserver`.
     */
    addMemoryAccessObserver(address: number, onRead: MemoryAccessObserver | null, onWrite: MemoryAccessObserver | null): MemoryObserverHandle;

    /**
     * Removes one registration. An unknown handle is ignored.
     */
    removeMemoryObserver(handle: MemoryObserverHandle): void;

    /**
     * Removes every registration.
     */
    removeMemoryObservers(): void;

    /**
     * The number of live registrations, across every `JsRiscV` instance.
     */
    countMemoryObservers(): number;

    /**
     * Gets the next statement to be executed.
     * @returns The next `JsProgramStatement`.
     */
    getNextStatement(): JsProgramStatement;

    /**
     * Sets the value of a register. Outside a Poke the write is direct: it records no undo step.
     * Inside a Poke it joins the open transaction, except for `zero`, which holds no value and so
     * is left alone.
     * @param register The name of the register.
     * @param value The value to set the register to.
     */
    setRegisterValue(register: RegisterName, high: number, low: number): void;

    /**
     * Checks if the simulation has terminated.
     * @returns True if the simulation has terminated, false otherwise.
     */
    terminated: boolean;
}



/**
 * Creates a RISC-V simulator from a virtual source tree and its entry file.
 *
 * Source paths are canonical, root-relative, case-sensitive POSIX paths. The source set is
 * snapshotted by this call; only the entry file and files reached through `.include` are assembled.
 * @param files Source text keyed by canonical source path.
 * @param entryFile Canonical path of the file from which include expansion starts.
 * @returns A new `JsRiscV` object.
 */
export function makeRiscVFromFiles(files: RISCVSourceSet, entryFile: string): JsRiscV {
    if (files === null || typeof files !== 'object' || Array.isArray(files)) {
        throw new TypeError('Source set must be an object')
    }
    if (typeof entryFile !== 'string') {
        throw new TypeError('Entry file must be a string')
    }
    const entries = Object.entries(files)
    for (const [sourcePath, source] of entries) {
        if (typeof source !== 'string') {
            throw new TypeError(`Source content must be a string: ${sourcePath}`)
        }
    }
    initializeRISCV()
    return _makeRiscVFromFiles(
        entries.map(([sourcePath]) => sourcePath),
        entries.map(([, source]) => source),
        entryFile,
    ) as JsRiscV
}

/**
 * Initializes the RISCV simulator.
 */
function initializeRISCV(): void {
    _initializeRISCV()
}

export function bigintToHighLow(value: bigint): [high: number, low: number] {
    const high = Number((value >> 32n) & 0xFFFFFFFFn)
    const low = Number(value & 0xFFFFFFFFn)
    return [high, low]
}

/**
 * The inverse of `bigintToHighLow`: composes the unsigned 64 bit value of a high/low pair, as the
 * register file getters return them. Both halves are taken unsigned, so a half the core hands over
 * as a negative int still lands in the right place.
 */
export function highLowToBigint(high: number, low: number): bigint {
    return (BigInt(high >>> 0) << 32n) | BigInt(low >>> 0)
}
