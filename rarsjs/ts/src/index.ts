//@ts-ignore
import {makeRiscVfromSource as _makeRiscVfromSource, initializeRISCV as _initializeRISCV, getInstructionSet as _getInstructionSet, setIs64Bit as _setIs64Bit, is64Bit as _is64Bit} from './generated/rars'


export type JsInstructionToken = {
    sourceLine: number;
    sourceColumn: number;
    originalSourceLine: number;
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
    line: string;
    tokens: JsInstructionToken[]
}

export type RISCVAssembleError = {
    isWarning: boolean
    message: string
    macroExpansionHistory: string
    filename: string
    lineNumber: number
    columnNumber: number
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
    public static makeRiscVFromSource = makeRiscVfromSource
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
    /**
     * The line number in the original source code.
     */
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
    DO_NOTHING
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
     * The program counter value before the action.
     */
    readonly pc: number;
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
     * Gets the 8 condition flags.
     */
    getConditionFlags(): number[];


    /**
     * Sets the size of the undo stack, must be called before assembling the program.
     * @param size
     */
    setUndoSize(size: number): void;

    /**
     * Undoes the last instruction executed.
     */
    undo(): void;


    /**
     * Gets the statement at the given address.
     * @param address
     */
    getStatementAtAddress(address: number): JsProgramStatement;

    /**
     * Gets the statement at the given source line.
     * @param line
     */
    getStatementAtSourceLine(line: number): JsProgramStatement;


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


    getParsedStatements(): JsInstructionToken[]


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
     * Gets the undo stack.
     * @returns An array of `JsBackStep` objects representing the history of the simulation.
     */
    getUndoStack(): JsBackStep[];

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
     * Unlike `readMemoryBytes`, this writes the way the program does: it notifies write observers
     * and, while undo is enabled, records an undo step per byte. Use `setPeripheralWord` for a
     * device keeping its own register up to date.
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
     * Gets the index of the current statement in the assembled program.
     * @returns The index of the current statement.
     */
    getCurrentStatementIndex(): number;

    /**
     * Gets the next statement to be executed.
     * @returns The next `JsProgramStatement`.
     */
    getNextStatement(): JsProgramStatement;

    /**
     * Sets the value of a register.
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
 * Creates a new RISCV simulator from the given source code.
 * @param source The source code to assemble.
 * @returns A new `JsRiscV` object.
 */
function makeRiscVfromSource(source: string): JsRiscV {
    _initializeRISCV()
    return _makeRiscVfromSource(source) as JsRiscV
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
