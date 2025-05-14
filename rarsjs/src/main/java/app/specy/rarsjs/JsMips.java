package app.specy.marsjs;

import app.specy.mars.Globals;
import app.specy.mars.MIPS;
import app.specy.mars.ProcessingException;
import app.specy.mars.ProgramStatement;
import app.specy.mars.mips.hardware.AddressErrorException;
import app.specy.mars.mips.hardware.Coprocessor1;
import app.specy.mars.mips.hardware.Register;
import app.specy.mars.mips.hardware.RegisterFile;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JsMips {
    private MIPS main;
    private static JsMIPSIO ioHandler;

    private JsMips(MIPS main) {
        this.main = main;
    }

    private static JsMIPSIO getIOHandler() {
        if (ioHandler == null) {
            ioHandler = new JsMIPSIO();
            MIPS.setIo(ioHandler);
        }
        return ioHandler;
    }

    @JSExport
    public static void initializeMIPS() {
        MIPS.initializeMIPS();
    }

    @JSExport
    public static JsMips makeMipsfromSource(String source) throws ProcessingException {
        JsMips.getIOHandler(); // Ensure that the IO handler is initialized
        return new JsMips(MIPS.fromSource(source));
    }

    @JSExport
    public JsCompilationResult assemble() throws ProcessingException {
        try{
            return new JsCompilationResult(this.main.assemble());
        }catch (ProcessingException e) {
            return new JsCompilationResult(e.errors());
        }
    }

    @JSExport
    public JsMipsTokenizedLine[] getTokenizedLines() {
        return this.main.getTokens().stream().map((v) -> {
            JsMipsToken[] tokens = new JsMipsToken[v.size()];
            for (int i = 0; i < v.size(); i++) {
                tokens[i] = new JsMipsToken(v.get(i));
            }
            return new JsMipsTokenizedLine(v.getProcessedLine(), tokens);
        }).toArray(JsMipsTokenizedLine[]::new);
    }

    @JSExport
    public void initialize(boolean startAtMain) {
        this.main.initialize(startAtMain);
    }

    @JSExport
    public boolean step() throws ProcessingException {
        return this.main.step();
    }

    @JSExport
    public boolean simulateWithLimit(int limit) throws ProcessingException {
        return this.main.simulate(limit);
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

    @JSExport
    public int[] getConditionFlags() {
        int[] flags = new int[8];
        for (int i = 0; i < 8; i++) {
            flags[i] = Coprocessor1.getConditionFlag(i);
        }
        return flags;
    }

    @JSExport
    public boolean simulateWithBreakpoints(int[] breakpoints) throws ProcessingException {
        return this.main.simulate(breakpoints);
    }

    @JSExport
    public boolean simulateWithBreakpointsAndLimit(int[] breakpoints, int limit) throws ProcessingException {
        return this.main.simulate(breakpoints, limit);
    }

    @JSExport
    public int getRegisterValue(String register) {
        return RegisterFile.getUserRegister(register).getValue();
    }

    @JSExport
    public void registerHandler(String name, JSFunction handler) {
        getIOHandler().registerHandler(name, handler);
    }

    @JSProperty
    @JSExport
    public int getStackPointer() {
        return RegisterFile.getUserRegister("$sp").getValue();
    }

    @JSProperty
    @JSExport
    public int getProgramCounter() {
        return RegisterFile.getProgramCounter();
    }

    @JSExport
    public int[] getRegistersValues() {
        return Arrays.stream(RegisterFile.getRegisters()).mapToInt(Register::getValue).toArray();
    }


    @JSExport
    public int getHi(){
        return RegisterFile.getValue(33);
    }

    @JSExport
    public int getLo(){
        return RegisterFile.getValue(34);
    }

    @JSExport
    public JsBackStep[] getUndoStack() {
        return Arrays.stream(this.main.getProgram().getBackStepper().getBackStepsStack().getStack()).map(JsBackStep::new).toArray(JsBackStep[]::new);
    }

    @JSExport
    public int[] readMemoryBytes(int address, int length) throws AddressErrorException {
        int[] memory = new int[length];
        for (int i = 0; i < length; i++) {
            memory[i] = Globals.memory.getByte(address + i);
        }
        return memory;
    }

    @JSExport
    public void setMemoryBytes(int address, int[] bytes) throws AddressErrorException {
        for (int i = 0; i < bytes.length; i++) {
            Globals.memory.setByte(address + i, bytes[i]);
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
    public int getCurrentStatementIndex() {
        return this.main.getStatementAtAddress(this.getProgramCounter()).getSourceLine();
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
    public JsProgramStatement getStatementAtSourceLine(int line) {
        ProgramStatement s = this.main.getAddressFromSourceLine(line);
        if(s == null) {
            return null;
        }
        return new JsProgramStatement(s);
    }

    @JSExport
    public static JsInstruction[] getInstructionSet() {
        return MIPS.getInstructionSet().getInstructionList().stream().map(JsInstruction::new).toArray(JsInstruction[]::new);
    }

    @JSExport
    public void setRegisterValue(String register, int value) {
        RegisterFile.getUserRegister(register).setValue(value);
    }

    @JSProperty
    @JSExport
    public boolean terminated() {
        return this.main.hasTerminated();
    }
}
