package app.specy.rarsjs;

import app.specy.rars.*;
import app.specy.rars.assembler.SourceLine;
import app.specy.rars.assembler.TokenList;
import app.specy.rars.riscv.fs.MemoryFileSystem;
import app.specy.rars.riscv.hardware.AddressErrorException;
import app.specy.rars.riscv.hardware.Register;
import app.specy.rars.riscv.hardware.RegisterFile;
import app.specy.rars.simulator.Simulator;
import org.teavm.jso.JSExceptions;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSFunction;
import org.teavm.jso.core.JSNumber;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.function.JSConsumer;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JsRiscV {
    private RARS main;
    private static JsRISCVIO ioHandler;

    private JsRiscV(RARS main) {
        this.main = main;
    }

    private static JsRISCVIO getIOHandler() {
        if (ioHandler == null) {
            ioHandler = new JsRISCVIO();
            RARS.setIo(ioHandler);
        }
        return ioHandler;
    }

    @JSExport
    public static void initializeRISCV() {
        RARS.initializeRISCV();
    }

    @JSExport
    public static JsRiscV makeRiscVFromFiles(String[] sourcePaths, String[] sources, String entryFile) {
        JsRiscV.getIOHandler(); // Ensure that the IO handler is initialized
        if (sourcePaths == null || sources == null || sourcePaths.length != sources.length) {
            throw new IllegalArgumentException("Source paths and contents must have the same length");
        }
        MemoryFileSystem files = new MemoryFileSystem();
        for (int i = 0; i < sourcePaths.length; i++) {
            files.write(sourcePaths[i], sources[i]);
        }
        return new JsRiscV(RARS.fromFs(entryFile, files));
    }

    @JSExport
    public JsCompilationResult assemble() {
        try{
            return new JsCompilationResult(this.main.assemble());
        }catch (AssemblyException e) {
            return new JsCompilationResult(e.errors());
        }
    }

    @JSExport
    public JsRiscVTokenizedLine[] getTokenizedLines() {
        List<TokenList> tokenizedLines = this.main.getTokens();
        List<SourceLine> sourceLines = this.main.getSourceLines();
        JsRiscVTokenizedLine[] result = new JsRiscVTokenizedLine[tokenizedLines.size()];
        for (int lineIndex = 0; lineIndex < tokenizedLines.size(); lineIndex++) {
            TokenList tokenizedLine = tokenizedLines.get(lineIndex);
            SourceLine sourceLine = sourceLines.get(lineIndex);
            JsRiscVToken[] tokens = new JsRiscVToken[tokenizedLine.size()];
            for (int tokenIndex = 0; tokenIndex < tokenizedLine.size(); tokenIndex++) {
                tokens[tokenIndex] = new JsRiscVToken(tokenizedLine.get(tokenIndex));
            }
            result[lineIndex] = new JsRiscVTokenizedLine(sourceLine.getSourcePath(),
                    sourceLine.getLineNumber(), sourceLine.getOriginalSource(),
                    sourceLine.getProcessedSource(), tokens);
        }
        return result;
    }

    @JSExport
    public void initialize(boolean startAtMain) {
        this.main.initialize(startAtMain);
    }


    /*
     * Simulation runs inside a single long-lived TeaVM coroutine ("green thread"), so that a
     * JS IO handler returning a promise can suspend the Java stack and resume once it settles.
     *
     * The coroutine is started once and then parked on a JS promise between tasks. Waking it
     * costs a microtask, whereas starting a fresh coroutine per call (Thread.start, which is what
     * JSPromise.callAsync does) goes through setTimeout and costs a full macrotask - about 1ms
     * per step, which is far too slow for instruction-level stepping.
     */
    private static final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private static JSConsumer<JSObject> unpark;
    private static boolean workerStarted;

    private interface Body {
        int run() throws SimulationException;
    }

    private static void workerLoop() {
        while (true) {
            while (!tasks.isEmpty()) {
                Runnable task = tasks.poll();
                try {
                    task.run();
                } catch (Throwable ignored) {
                    // run() already settles the promise for every outcome, so there is nowhere
                    // left to report this; swallow it rather than killing the worker.
                }
            }
            JSPromise<JSObject> parked = JSPromise.create((resolve, reject) -> unpark = resolve);
            parked.await();
            unpark = null;
        }
    }

    private static void submit(Runnable task) {
        tasks.add(task);
        if (!workerStarted) {
            workerStarted = true;
            JSPromise.runAsync(JsRiscV::workerLoop);
            return;
        }
        JSConsumer<JSObject> resume = unpark;
        if (resume != null) {
            unpark = null;
            resume.accept(null);
        }
    }

    private static JSPromise<JSNumber> run(Body body) {
        return JSPromise.create((resolve, reject) -> submit(() -> {
            int result;
            try {
                result = body.run();
            } catch (Throwable t) {
                reject.accept(JSExceptions.getJSException(t));
                return;
            }
            resolve.accept(JSNumber.valueOf(result));
        }));
    }

    @JSExport
    public JSPromise<JSNumber> step() {
        return run(() -> this.main.step().ordinal());
    }

    @JSExport
    public int getStopReason() {
        // Null until a simulation has run; StopReason.NONE on the TS side.
        Simulator.Reason reason = this.main.getStopReason();
        return reason == null ? -1 : reason.ordinal();
    }

    @JSExport
    public JSPromise<JSNumber> simulate() {
        return run(() -> this.main.simulate(-1).ordinal());
    }

    @JSExport
    public JSPromise<JSNumber> simulateWithLimit(int limit) {
        return run(() -> this.main.simulate(limit).ordinal());
    }

    @JSExport
    public JsStackFrame[] getCallStack(){
        List<JsStackFrame> stack = new ArrayList<>();
        for(int i = 0; i < this.main.getCallStack().length; i++) {
            stack.add(new JsStackFrame(this.main.getCallStack()[i]));
        }
        return stack.toArray(new JsStackFrame[0]);
    }

    @JSExport
    public String getLabelAtAddress(int address){
        return this.main.getLabelAtAddress(address);
    }

   /*
    @JSExport
    public int[] getConditionFlags() {
        int[] flags = new int[8];
        for (int i = 0; i < 8; i++) {
            flags[i] = Coprocessor1.getConditionFlag(i);
        }
        return flags;
    }
    */

    @JSExport
    public JSPromise<JSNumber> simulateWithBreakpoints(int[] breakpoints) {
        return run(() -> this.main.simulate(breakpoints).ordinal());
    }

    @JSExport
    public JSPromise<JSNumber> simulateWithBreakpointsAndLimit(int[] breakpoints, int limit) {
        return run(() -> this.main.simulate(limit, breakpoints).ordinal());
    }

    @JSExport
    public int getRegisterValue(String register) {
        return (int) RegisterFile.getRegister(register).getValue();
    }

    @JSExport
    public BigInteger getRegisterValueLong(String register) {
        return BigInteger.valueOf(RegisterFile.getRegister(register).getValue());
    }

    @JSProperty
    @JSExport
    public String getStackPointerLong() {
        return BigInteger.valueOf(RegisterFile.getStackPointerRegister().getValue()).toString();
    }

    @JSProperty
    @JSExport
    public String getProgramCounterLong() {
        return BigInteger.valueOf(RegisterFile.getProgramCounterRegister().getValue()).toString();
    }

    @JSExport
    public String[] getRegistersValuesLong() {
        Register[] registers = RegisterFile.getRegisters();
        String[] values = new String[registers.length];
        for (int i = 0; i < registers.length; i++) {
            values[i] = BigInteger.valueOf(registers[i].getValue()).toString();
        }
        return values;
    }




    @JSExport
    public void registerHandler(String name, JSFunction handler) {
        getIOHandler().registerHandler(name, handler);
    }

    @JSProperty
    @JSExport
    public int getStackPointer() {
        return (int) RegisterFile.getStackPointerRegister().getValue();
    }



    @JSExport()
    public static boolean is64Bit() {
        return RARS.is64Bit();
    }

    @JSExport()
    public static void setIs64Bit(boolean is64Bit) {
        RARS.setIs64Bit(is64Bit);
    }

    @JSProperty
    @JSExport
    public int getProgramCounter() {
        return RegisterFile.getProgramCounter();
    }


    @JSExport
    public int[] getRegistersValues() {
        return Arrays.stream(RegisterFile.getRegisters()).mapToInt((v) -> (int) v.getValue()).toArray();
    }


    @JSExport
    public JsBackStep[] getUndoStack() {
        return Arrays.stream(this.main.getProgram().getBackStepper().getBackStepsStack().getStack()).map(JsBackStep::new).toArray(JsBackStep[]::new);
    }

    @JSExport
    public int[] readMemoryBytes(int address, int length) throws AddressErrorException {
        int[] memory = new int[length];
        for (int i = 0; i < length; i++) {
            // No notification: the host inspecting memory is not the program reading it, and a
            // memory viewer must not make a memory-mapped register consume its pending input.
            memory[i] = Globals.memory.getByteNoNotify(address + i);
        }
        return memory;
    }

    @JSExport
    public void setMemoryBytes(int address, int[] bytes) throws AddressErrorException {
        for (int i = 0; i < bytes.length; i++) {
            Globals.memory.setByte(address + i, bytes[i]);
        }
    }

    @JSExport
    public void setPeripheralWord(double address, int value) throws AddressErrorException {
        Globals.memory.setRawWordNoNotify(toAddress(address), value);
    }

    /**
     * Addresses cross from JavaScript as plain numbers, and one above 2^31-1 - which every
     * memory-mapped register is - stays positive instead of wrapping into a negative int.
     * Normalizing here means 0xffff0000 and 0xffff0000 | 0 name the same word, rather than the
     * unsigned form quietly registering an observer that can never match an access.
     */
    private static int toAddress(double address) {
        return (int) (long) address;
    }

    /*
     * Memory observers live on the Memory singleton, which assemble() and initialize() only clear
     * the contents of, so a registration survives both exactly like a registered IO handler and,
     * like one, is shared by every JsRiscV instance. The registrations are mirrored here because
     * Memory.deleteObserver leaves an empty observable behind for every removal and every memory
     * access walks that collection; removal therefore rebuilds it from the survivors.
     */
    private static final List<JsMemoryObserver> memoryObservers = new ArrayList<>();
    private static int nextMemoryObserverHandle = 1;

    @JSExport
    public int addMemoryWriteObserver(double startAddress, double endAddress, JSFunction handler)
            throws AddressErrorException {
        return addMemoryObserver(JsMemoryObserver.overRange(nextMemoryObserverHandle,
                toAddress(startAddress), toAddress(endAddress), handler));
    }

    @JSExport
    public int addMemoryAccessObserver(double address, JSFunction onRead, JSFunction onWrite)
            throws AddressErrorException {
        return addMemoryObserver(
                JsMemoryObserver.atWord(nextMemoryObserverHandle, toAddress(address), onRead, onWrite));
    }

    @JSExport
    public void removeMemoryObserver(int handle) {
        for (int i = 0; i < memoryObservers.size(); i++) {
            if (memoryObservers.get(i).handle == handle) {
                memoryObservers.remove(i);
                rebuildMemoryObservers();
                return;
            }
        }
    }

    @JSExport
    public void removeMemoryObservers() {
        memoryObservers.clear();
        Globals.memory.deleteObservers();
    }

    @JSExport
    public int countMemoryObservers() {
        return memoryObservers.size();
    }

    private static int addMemoryObserver(JsMemoryObserver observer) throws AddressErrorException {
        // Registering first leaves the mirror untouched when the range is rejected.
        Globals.memory.addObserver(observer, observer.startAddress, observer.endAddress);
        memoryObservers.add(observer);
        nextMemoryObserverHandle++;
        return observer.handle;
    }

    private static void rebuildMemoryObservers() {
        Globals.memory.deleteObservers();
        for (JsMemoryObserver observer : memoryObservers) {
            try {
                Globals.memory.addObserver(observer, observer.startAddress, observer.endAddress);
            } catch (AddressErrorException alreadyValidated) {
                // Every surviving registration passed this same check when it was added.
            }
        }
    }


    @JSProperty
    @JSExport
    public boolean canUndo() {
        return !this.main.getProgram().getBackStepper().empty();
    }

    @JSExport
    public void setUndoSize(int size) {
        Globals.maximumBacksteps = size;
    }

    @JSExport
    void setUndoEnabled(boolean enabled) {
        this.main.getProgram().getBackStepper().setEnabled(enabled);
    }

    @JSExport
    public void undo() {
        this.main.getProgram().getBackStepper().backStep();
    }

    @JSExport
    public JsProgramStatement getNextStatement() {
        return new JsProgramStatement(this.main.getStatementAtAddress(this.getProgramCounter()));
    }

    @JSExport
    public JsProgramStatement getStatementAtAddress(int address) {
        return new JsProgramStatement(this.main.getStatementAtAddress(address));
    }

    @JSExport
    public JsProgramStatement[] getCompiledStatements() {
        List<ProgramStatement> statements = this.main.getStatements();
        JsProgramStatement[] jsStatements = new JsProgramStatement[statements.size()];
        for (int i = 0; i < statements.size(); i++) {
            jsStatements[i] = new JsProgramStatement(statements.get(i));
        }
        return jsStatements;
    }

    @JSExport
    public JsProgramStatement[] getParsedStatements() {
        List<ProgramStatement> statements = this.main.getParsedStatements();
        JsProgramStatement[] jsStatements = new JsProgramStatement[statements.size()];
        for (int i = 0; i < statements.size(); i++) {
            jsStatements[i] = new JsProgramStatement(statements.get(i));
        }
        return jsStatements;
    }

    @JSExport
    public JsProgramStatement[] getStatementsAtSourceLocation(String sourcePath, double sourceLine) {
        if (!Double.isFinite(sourceLine) || sourceLine < 1 || sourceLine != Math.floor(sourceLine)
                || sourceLine > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Source line must be a positive integer");
        }
        return this.main.getStatementsAtSourceLocation(sourcePath, (int) sourceLine).stream()
                .map(JsProgramStatement::new)
                .toArray(JsProgramStatement[]::new);
    }

    @JSExport
    public static JsInstruction[] getInstructionSet() {
        return RARS.getInstructionSet().getInstructionList().stream().map(JsInstruction::new).toArray(JsInstruction[]::new);
    }

    @JSExport
    public void setRegisterValue(String register, int high, int low) {
        RegisterFile.getRegister(register).setValue(((long) high << 32) | (low & 0xFFFFFFFFL));
    }

    @JSProperty
    @JSExport
    public boolean terminated() {
        return this.main.hasTerminated();
    }
}
