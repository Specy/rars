//@ts-ignore
import {makeMipsfromSource as _makeMipsfromSource, initializeMIPS as _initializeMIPS, getInstructionSet as _getInstructionSet} from './generated/mars'


export type JsInstructionToken = {
    sourceLine: number;
    sourceColumn: number;
    originalSourceLine: number;
    value: string;
    type: string
}



/*
public abstract int openFile(String filename, int flags, boolean append) throws MIPSIOError;
    public abstract void closeFile(int fileDescriptor) throws MIPSIOError;
    public abstract void writeFile(int fileDescriptor, byte[] buffer) throws MIPSIOError;
    public abstract int readFile(int fileDescriptor, byte[] destination, int length) throws MIPSIOError;


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
    readFile: {in: [fileDescriptor: number, destination: number[], length: number], out: number}
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
    sleep: {in: [milliseconds: number], out: void}
    stdIn: {in: [buffer: number[], length: number], out: void}
    stdOut: {in: [buffer: number[]], out: void}
}

export type JsInstruction = {
    name: string;
    example: string;
    description: string;
    tokens: JsInstructionToken[];
}

export type MipsTokenizedLine = {
    line: string;
    tokens: JsInstructionToken[]
}

export type MIPSAssembleError = {
    isWarning: boolean
    message: string
    macroExpansionHistory: string
    filename: string
    lineNumber: number
    columnNumber: number
}

export type MIPSAssembleResult = {
    report: string
    errors: MIPSAssembleError[]
    hasErrors: boolean
}


export class MIPS {
    public static makeMipsFromSource = makeMipsfromSource
    public static initializeMIPS = initializeMIPS
    public static getInstructionSet(){
        return _getInstructionSet() as JsInstruction[]
    }
}

export type JsMipsStackFrame = {
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
    MEMORY_RESTORE_WORD,
    MEMORY_RESTORE_HALF,
    MEMORY_RESTORE_BYTE,
    REGISTER_RESTORE,
    PC_RESTORE,
    COPROC0_REGISTER_RESTORE,
    COPROC1_REGISTER_RESTORE,
    COPROC1_CONDITION_CLEAR,
    COPROC1_CONDITION_SET,
    DO_NOTHING, // instruction does not write anything.
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

/**
 * All MIPS register names.
 */
export type RegisterName =
    '$zero'
    | '$at'
    | '$v0'
    | '$v1'
    | '$a0'
    | '$a1'
    | '$a2'
    | '$a3'
    | '$t0'
    | '$t1'
    | '$t2'
    | '$t3'
    | '$t4'
    | '$t5'
    | '$t6'
    | '$t7'
    | '$s0'
    | '$s1'
    | '$s2'
    | '$s3'
    | '$s4'
    | '$s5'
    | '$s6'
    | '$s7'
    | '$t8'
    | '$t9'
    | '$k0'
    | '$k1'
    | '$gp'
    | '$sp'
    | '$fp'
    | '$ra';

type HandlerName = keyof HandlerMap


export type HandlerMapFns = {
    [K in HandlerName]: (...args: HandlerMap[K]['in']) => HandlerMap[K]['out']
}

export function registerHandlers(mips: JsMips, handlers: HandlerMapFns) {
    for (const [name, handler] of Object.entries(handlers)) {
        mips.registerHandler(name as HandlerName, handler as (...args: HandlerMap[HandlerName]['in']) => HandlerMap[HandlerName]['out'])
    }
}

export function unimplementedHandler(name: HandlerName) {
    return function () {
        throw new Error(`Handler ${name} is not implemented`)
    }
}

/**
 * Interface for interacting with a MIPS simulator.
 */
export interface JsMips {
    /**
     * Assembles the program.
     */
    assemble(): MIPSAssembleResult;

    /**
     * Initializes the simulator.
     * @param startAtMain If true, starts execution at the 'main' label. Otherwise, starts at the first instruction.
     */
    initialize(startAtMain: boolean): void;

    /**
     * Executes a single instruction.
     * @returns True if the execution is complete, false otherwise.
     */
    step(): boolean;


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


    getTokenizedLines(): MipsTokenizedLine[]
    /**
     * Checks if the simulation can be undone.
     * @returns True if the simulation can be undone, false otherwise.
     * */
    canUndo: boolean;



    /**
     * Gets the call stack.
     * @returns An array of memory addresses representing the call stack.
     */
    getCallStack(): JsMipsStackFrame[]



    /**
     * Gets the compiled statements.
     * @returns An array of `JsProgramStatement` objects representing the compiled program.
     */
    getCompiledStatements(): JsProgramStatement[]


    getParsedStatements(): JsInstructionToken[]


    getHi(): number;

    getLo(): number;


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
     * Simulates the program for a limited number of instructions.
     * @param limit The maximum number of instructions to execute.
     * @returns True if the execution is complete, false otherwise.
     */
    simulateWithLimit(limit: number): boolean;

    /**
     * Simulates the program until a breakpoint is reached.
     * @param breakpoints An array of memory addresses where the simulation should pause.
     * @returns True if the execution is complete, false otherwise.
     */
    simulateWithBreakpoints(breakpoints: number[]): boolean;

    /**
     * Simulates the program with both breakpoints and a limit.
     * @param breakpoints An array of memory addresses where the simulation should pause.
     * @param limit The maximum number of instructions to execute.
     * @returns True if the execution is complete, false otherwise.
     */
    simulateWithBreakpointsAndLimit(breakpoints: number[], limit: number): boolean;

    /**
     * Gets the value of a register.
     * @param register The name of the register.
     * @returns The value of the register.
     */
    getRegisterValue(register: RegisterName): number;

    /**
     * Registers a handler function for a specific event or condition.
     * @param name The name of the event or condition.
     * @param handler The handler function to be called when the event occurs. The function signature depends on the event name.
     */
    registerHandler<T extends HandlerName>(name: T, handler: (...args: HandlerMap[T]['in']) => HandlerMap[T]['out']): void;

    /**
     * Gets the current value of the stack pointer.
     * @returns The value of the stack pointer.
     */
    stackPointer: number;

    /**
     * Gets the current value of the program counter.
     * @returns The value of the program counter.
     */
    programCounter: number;

    /**
     * Gets the values of all registers.
     * @returns An array containing the register values. The order of the values is implementation defined.
     */
    getRegistersValues(): number[];

    /**
     * Gets the undo stack.
     * @returns An array of `JsBackStep` objects representing the history of the simulation.
     */
    getUndoStack(): JsBackStep[];

    /**
     * Reads a sequence of bytes from memory.
     * @param address The starting memory address.
     * @param length The number of bytes to read.
     * @returns An array of bytes read from memory.
     */
    readMemoryBytes(address: number, length: number): number[];

    /**
     * Writes a sequence of bytes to memory.
     * @param address The starting memory address.
     * @param bytes An array of bytes to write to memory.
     */
    setMemoryBytes(address: number, bytes: number[]): void;

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
    setRegisterValue(register: RegisterName, value: number): void;

    /**
     * Checks if the simulation has terminated.
     * @returns True if the simulation has terminated, false otherwise.
     */
    terminated: boolean;
}



/**
 * Creates a new MIPS simulator from the given source code.
 * @param source The source code to assemble.
 * @returns A new `JsMips` object.
 */
function makeMipsfromSource(source: string): JsMips {
    initializeMIPS()
    return _makeMipsfromSource(source) as JsMips
}

/**
 * Initializes the MIPS simulator.
 */
function initializeMIPS(): void {
    _initializeMIPS()
}
