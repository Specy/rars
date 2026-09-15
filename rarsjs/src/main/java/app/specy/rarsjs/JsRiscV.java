package app.specy.rarsjs;

import app.specy.rars.*;
import app.specy.rars.assembler.SourceLine;
import app.specy.rars.assembler.TokenList;
import app.specy.rars.riscv.fs.MemoryFileSystem;
import app.specy.rars.riscv.hardware.AddressErrorException;
import app.specy.rars.riscv.hardware.ControlAndStatusRegisterFile;
import app.specy.rars.riscv.hardware.FloatingPointRegisterFile;
import app.specy.rars.riscv.hardware.Register;
import app.specy.rars.riscv.hardware.RegisterFile;
import app.specy.rars.simulator.BackStepper;
import app.specy.rars.simulator.Simulator;
import org.teavm.jso.JSExceptions;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSFunction;
import org.teavm.jso.core.JSNumber;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.function.JSConsumer;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /*
     * How many step/simulate calls of THIS core are in flight. A task is counted from the moment it
     * is submitted until its body returns, which includes the time the coroutine spends parked on
     * an IO handler's promise: the simulator's state is half-written then, so a poke must be
     * refused. The count is per instance, not static: a host builds throwaway cores (the editor
     * assembles one to check the source), and a step on one of those must not refuse a poke on
     * another.
     */
    private int executingInstructions;

    private JSPromise<JSNumber> run(Body body) {
        executingInstructions++;
        boolean submitted = false;
        try {
            JSPromise<JSNumber> promise = JSPromise.create((resolve, reject) -> submit(() -> {
                int result;
                try {
                    result = body.run();
                } catch (Throwable t) {
                    reject.accept(JSExceptions.getJSException(t));
                    return;
                } finally {
                    // Exactly once, on every path out of the body, so that a failed step cannot
                    // leave the core refusing pokes for the rest of the session.
                    executingInstructions--;
                }
                resolve.accept(JSNumber.valueOf(result));
            }));
            submitted = true;
            return promise;
        } finally {
            if (!submitted) {
                // The task never reached the queue, so its body will never run the decrement.
                executingInstructions--;
            }
        }
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


    /*
     * The floating point and control and status register files, as flat int arrays of high/low
     * pairs: element 2i is the high 32 bits of register i and 2i+1 its low 32 bits. Both files hold
     * 64 bit values, and a BigInteger (or a decimal String, as the general registers use) per
     * register per panel refresh is exactly the long work TeaVM compiles into allocating BigInt
     * operations. Values are read without notifying observers, because the host inspecting a
     * register is not the program reading it.
     */

    /**
     * @return 64 ints, high/low per register, in FloatingPointRegisterFile.getRegisters() order:
     *         ft0-ft7, fs0, fs1, fa0-fa7, fs2-fs11, ft8-ft11.
     */
    @JSExport
    public int[] getFloatingPointRegistersValues() {
        Register[] registers = FloatingPointRegisterFile.getRegisters();
        int[] values = new int[registers.length * 2];
        for (int i = 0; i < registers.length; i++) {
            long value = registers[i].getValueNoNotify();
            values[i * 2] = (int) (value >>> 32);
            values[i * 2 + 1] = (int) value;
        }
        return values;
    }

    /**
     * Writes one floating point register. Outside a poke the write is direct, bypassing the back
     * stepper: a value the host presets is not something the program did, so it must not become an
     * undo entry. Inside a poke it joins the open transaction.
     *
     * @param index Position in the order getFloatingPointRegistersValues() returns, 0 to 31.
     */
    @JSExport
    public void setFloatingPointRegisterValue(int index, int high, int low) {
        Register[] registers = FloatingPointRegisterFile.getRegisters();
        if (index < 0 || index >= registers.length) {
            throw new IllegalArgumentException("Floating point register index out of range: " + index);
        }
        Register target = registers[index];
        long value = ((long) high << 32) | (low & 0xFFFFFFFFL);
        if (openPoke == null) {
            target.setValue(value);
            return;
        }
        long old = target.getValueNoNotify();
        if (old == value) {
            return; // unchanged: nothing to journal and nothing to undo
        }
        journalRegister(target, old);
        // The register file's own write path, so the back stepper records the restore; a poke is
        // open, so it lands in that transaction rather than on the stack.
        FloatingPointRegisterFile.updateRegisterLong(target.getNumber(), value);
    }

    /**
     * @return 34 ints, high/low per register, in ControlAndStatusRegisterFile.getRegisters() order:
     *         ustatus, fflags, frm, fcsr, uie, utvec, uscratch, uepc, ucause, utval, uip, cycle,
     *         time, instret, cycleh, timeh, instreth. The linked registers (fflags, frm and the
     *         *h halves) read through the register they alias, and getRegisters() settles the
     *         lazily counted cycle and instret first, so no value is behind.
     */
    @JSExport
    public int[] getControlAndStatusRegistersValues() {
        Register[] registers = ControlAndStatusRegisterFile.getRegisters();
        int[] values = new int[registers.length * 2];
        for (int i = 0; i < registers.length; i++) {
            long value = registers[i].getValueNoNotify();
            values[i * 2] = (int) (value >>> 32);
            values[i * 2 + 1] = (int) value;
        }
        return values;
    }

    /**
     * Writes one control and status register through the register's own setValue: a linked register
     * writes the register it aliases, a masked register keeps the bits it does not own, and a read
     * only register is written as the back door does, because the host is not the program. Outside
     * a poke the write records no undo entry; inside one it joins the open transaction, which
     * remembers the register's own value so that undoing the poke writes it back the same way.
     *
     * @param index Position in the order getControlAndStatusRegistersValues() returns, 0 to 16.
     */
    @JSExport
    public void setControlAndStatusRegisterValue(int index, int high, int low) {
        Register[] registers = ControlAndStatusRegisterFile.getRegisters();
        if (index < 0 || index >= registers.length) {
            throw new IllegalArgumentException("Control and status register index out of range: " + index);
        }
        Register target = registers[index];
        long value = ((long) high << 32) | (low & 0xFFFFFFFFL);
        if (openPoke == null) {
            target.setValue(value);
            return;
        }
        long old = target.getValueNoNotify();
        if (old == value) {
            return;
        }
        journalRegister(target, old);
        // No register file routes this write through the back stepper, so the transaction is told
        // about it directly, by the architectural CSR number the restore will look the register up
        // by.
        this.main.getProgram().getBackStepper().addPokeControlAndStatusRestore(target.getNumber(), old);
        target.setValue(value);
    }

    /**
     * The raw back step stack, newest first: one element per recorded step. An instruction occupies
     * one to three of them, a poke exactly one, whatever it wrote - the element with `isPoke` set,
     * which is what tells a poke apart from a host write made before anything ran, since both carry
     * pc -1. Use getUndoGroups() to read the history the way undo() pops it.
     */
    @JSExport
    public JSArray<JSObject> getUndoStack() {
        BackStepper.BackStep[] stack = this.main.getProgram().getBackStepper().getBackStepsStack().getStack();
        JSArray<JSObject> steps = JSArray.create(stack.length);
        for (int i = 0; i < stack.length; i++) {
            steps.set(i, JsBackStep.of(stack[i]));
        }
        return steps;
    }

    /**
     * The same history as getUndoStack(), grouped the way undo() pops it: one entry per executed
     * instruction or per poke, newest first. An instruction that records several back steps - a
     * `jal` restoring both `ra` and the program counter, and the counter decrement every
     * instruction pushes - is one entry, and so is a poke, which is one back step to begin with.
     */
    @JSExport
    public JSArray<JSObject> getUndoGroups() {
        BackStepper.BackStep[] stack = this.main.getProgram().getBackStepper().getBackStepsStack().getStack();
        List<JSObject> groups = new ArrayList<>();
        Set<Integer> livePokeGroups = new HashSet<>();
        int start = 0;
        while (start < stack.length) {
            int end = start + 1;
            while (end < stack.length && BackStepper.sameGroup(stack[end - 1], stack[end])) {
                end++;
            }
            JSArray<JSObject> steps = JSArray.create(end - start);
            for (int i = start; i < end; i++) {
                steps.set(i - start, JsBackStep.of(stack[i]));
            }
            if (stack[start].isPoke()) {
                int group = stack[start].getPokeGroup();
                livePokeGroups.add(group);
                groups.add(JsUndoGroup.poke(POKE_PC, steps, writesOfPoke(group)));
            } else {
                groups.add(JsUndoGroup.instruction(stack[start].getPc(), steps));
            }
            start = end;
        }
        // A poke whose entry has fallen off the circular stack can never be reported again.
        forgetPokeRecordsOtherThan(livePokeGroups);
        JSArray<JSObject> result = JSArray.create(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            result.set(i, groups.get(i));
        }
        return result;
    }

    /*
     * Pokes.
     *
     * A poke is a register or memory value the host changes between two instructions: one entry of
     * this same history, undone by one undo(). beginPoke() opens a transaction; until endPoke() the
     * setters write through the simulator's own paths, so the back stepper records what they
     * changed into that transaction, and they journal here what the value was, so that the entry
     * can say what it changed. Outside a transaction every setter is direct and records nothing,
     * which is what presetting a testcase needs.
     */

    /** The pc a poke reports: it belongs to no instruction, so it has no address of its own. */
    private static final int POKE_PC = -1;

    /*
     * Finished pokes are remembered here so that getUndoGroups() can report what they changed long
     * after the writes happened. Only a poke still on the back step stack can be reported, so the
     * list is pruned whenever the groups are read, and capped for the case where they never are.
     */
    private static final int MAX_POKE_RECORDS = 1024;

    private PokeJournal openPoke;
    private final List<PokeRecord> pokeRecords = new ArrayList<>();

    /**
     * Opens a poke transaction. Every write made by the setters until endPoke() becomes part of one
     * history entry, restored as a unit by one undo().
     *
     * @throws IllegalStateException if a poke is already open, or if an instruction is executing.
     */
    @JSExport
    public void beginPoke() {
        if (executingInstructions > 0) {
            throw new IllegalStateException("Cannot begin a poke while an instruction is executing");
        }
        if (openPoke != null) {
            throw new IllegalStateException("A poke is already open");
        }
        openPoke = new PokeJournal(this.main.getProgram().getBackStepper().beginPoke());
    }

    /**
     * Closes the open poke transaction.
     *
     * @return true if it recorded one history entry, false if it wrote nothing - or if undo is
     * disabled, in which case the writes stand but cannot be undone.
     * @throws IllegalStateException if no poke is open.
     */
    @JSExport
    public boolean endPoke() throws AddressErrorException {
        if (openPoke == null) {
            throw new IllegalStateException("No poke is open");
        }
        PokeJournal journal = openPoke;
        openPoke = null;
        if (!this.main.getProgram().getBackStepper().endPoke()) {
            return false;
        }
        pokeRecords.add(new PokeRecord(journal.group, buildWrites(journal)));
        while (pokeRecords.size() > MAX_POKE_RECORDS) {
            pokeRecords.remove(0);
        }
        return true;
    }

    /** Whether a poke transaction is open, so that the setters journal instead of writing through. */
    @JSExport
    public boolean pokeOpen() {
        return openPoke != null;
    }

    private BackStepper backStepper() {
        return this.main.isAssembled() ? this.main.getProgram().getBackStepper() : null;
    }

    private void journalRegister(Register register, long oldValue) {
        // The first write to a register in the transaction holds the value to restore; a later one
        // overwrites a value the poke itself put there.
        if (openPoke.registersByName.containsKey(register.getName())) {
            return;
        }
        PokeRegisterWrite write = new PokeRegisterWrite(register, oldValue);
        openPoke.registersByName.put(register.getName(), write);
        openPoke.registers.add(write);
    }

    private void journalMemory(int address, int oldByte) {
        // As for a register, the first write to an address holds the value to restore.
        Long key = address & 0xffffffffL;
        if (!openPoke.memoryOldBytes.containsKey(key)) {
            openPoke.memoryOldBytes.put(key, oldByte);
        }
    }

    /**
     * What the poke changed: every register it wrote, in the order it wrote them, then every run of
     * consecutive memory addresses it wrote, by ascending address. The new values are read now, at
     * the end of the transaction, so a value the poke wrote twice reports only its final state.
     */
    private static JSArray<JSObject> buildWrites(PokeJournal journal) throws AddressErrorException {
        List<JSObject> writes = new ArrayList<>();
        for (PokeRegisterWrite register : journal.registers) {
            writes.add(JsPokeWrite.register(register.register.getName(), register.oldValue,
                    register.register.getValueNoNotify()));
        }
        List<Long> addresses = new ArrayList<>(journal.memoryOldBytes.keySet());
        Collections.sort(addresses); // unsigned order: the keys are addresses widened to long
        int runStart = 0;
        while (runStart < addresses.size()) {
            int runEnd = runStart + 1;
            while (runEnd < addresses.size()
                    && addresses.get(runEnd).longValue() == addresses.get(runEnd - 1).longValue() + 1) {
                runEnd++;
            }
            int[] oldBytes = new int[runEnd - runStart];
            int[] newBytes = new int[runEnd - runStart];
            for (int i = runStart; i < runEnd; i++) {
                int address = (int) (long) addresses.get(i);
                oldBytes[i - runStart] = journal.memoryOldBytes.get(addresses.get(i));
                newBytes[i - runStart] = Globals.memory.getByteNoNotify(address) & 0xff;
            }
            writes.add(JsPokeWrite.memory((int) (long) addresses.get(runStart), oldBytes, newBytes));
            runStart = runEnd;
        }
        JSArray<JSObject> result = JSArray.create(writes.size());
        for (int i = 0; i < writes.size(); i++) {
            result.set(i, writes.get(i));
        }
        return result;
    }

    private JSArray<JSObject> writesOfPoke(int group) {
        for (PokeRecord record : pokeRecords) {
            if (record.group == group) {
                return record.writes;
            }
        }
        return JSArray.create(0);
    }

    private void forgetPokeRecordsOtherThan(Set<Integer> livePokeGroups) {
        for (int i = pokeRecords.size() - 1; i >= 0; i--) {
            if (!livePokeGroups.contains(pokeRecords.get(i).group)) {
                pokeRecords.remove(i);
            }
        }
    }

    /** One register the open poke has written, with the value to put back. */
    private static final class PokeRegisterWrite {
        final Register register;
        final long oldValue;

        PokeRegisterWrite(Register register, long oldValue) {
            this.register = register;
            this.oldValue = oldValue;
        }
    }

    /** What the open poke has written so far. */
    private static final class PokeJournal {
        final int group;
        final List<PokeRegisterWrite> registers = new ArrayList<>();
        final Map<String, PokeRegisterWrite> registersByName = new HashMap<>();
        /** Old byte per written address, the address widened to an unsigned long. */
        final Map<Long, Integer> memoryOldBytes = new HashMap<>();

        PokeJournal(int group) {
            this.group = group;
        }
    }

    /** A finished poke, kept for as long as its entry is on the back step stack. */
    private static final class PokeRecord {
        final int group;
        final JSArray<JSObject> writes;

        PokeRecord(int group, JSArray<JSObject> writes) {
            this.group = group;
            this.writes = writes;
        }
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
        if (openPoke != null) {
            for (int i = 0; i < bytes.length; i++) {
                int at = address + i;
                int old = Globals.memory.getByteNoNotify(at) & 0xff;
                if (old == (bytes[i] & 0xff)) {
                    continue; // unchanged: no write, no notification, nothing to undo
                }
                journalMemory(at, old);
                // The ordinary store, so observers hear it; the back stepper is open on a poke, so
                // the restore it records joins that poke rather than the last instruction's group.
                Globals.memory.setByte(at, bytes[i]);
            }
            return;
        }
        // A host write outside a poke is not a step: it writes the way the program does, so that a
        // memory mapped display still repaints, but records nothing. Left recording, each byte
        // would push a back step under the last executed instruction's address and the next undo
        // would revert the host's write together with that instruction.
        BackStepper backStepper = backStepper();
        boolean recording = backStepper != null && backStepper.enabled();
        if (recording) {
            backStepper.setEnabled(false);
        }
        try {
            for (int i = 0; i < bytes.length; i++) {
                Globals.memory.setByte(address + i, bytes[i]);
            }
        } finally {
            if (recording) {
                backStepper.setEnabled(true);
            }
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

    /**
     * Writes one general register. Outside a poke the write is direct, bypassing the back stepper;
     * inside a poke it joins the open transaction. `zero` is not writable inside a poke: it holds
     * no value, so there would be nothing for undo to restore.
     */
    @JSExport
    public void setRegisterValue(String register, int high, int low) {
        Register target = RegisterFile.getRegister(register);
        long value = ((long) high << 32) | (low & 0xFFFFFFFFL);
        if (openPoke == null) {
            target.setValue(value);
            return;
        }
        if (target.getNumber() == 0) {
            return;
        }
        long old = target.getValueNoNotify();
        if (old == value) {
            return;
        }
        journalRegister(target, old);
        RegisterFile.updateRegister(target.getNumber(), value);
    }

    @JSProperty
    @JSExport
    public boolean terminated() {
        return this.main.hasTerminated();
    }
}
