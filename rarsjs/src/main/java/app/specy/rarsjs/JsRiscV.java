package app.specy.rarsjs;

import app.specy.rars.*;
import app.specy.rars.riscv.hardware.AddressErrorException;
import app.specy.rars.riscv.hardware.Register;
import app.specy.rars.riscv.hardware.RegisterFile;
import app.specy.rars.simulator.Simulator;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSFunction;

import java.math.BigInteger;
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
    public static JsRiscV makeRiscVfromSource(String source) throws AssemblyException {
        JsRiscV.getIOHandler(); // Ensure that the IO handler is initialized
        return new JsRiscV(RARS.fromSource(source));
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
        return this.main.getTokens().stream().map((v) -> {
            JsRiscVToken[] tokens = new JsRiscVToken[v.size()];
            for (int i = 0; i < v.size(); i++) {
                tokens[i] = new JsRiscVToken(v.get(i));
            }
            return new JsRiscVTokenizedLine(v.getProcessedLine(), tokens);
        }).toArray(JsRiscVTokenizedLine[]::new);
    }

    @JSExport
    public void initialize(boolean startAtMain) {
        this.main.initialize(startAtMain);
    }

    @JSExport
    public Simulator.Reason step() throws SimulationException {
        return this.main.step();
    }

    @JSExport
    public Simulator.Reason getStopReason() {
        return this.main.getStopReason();
    }

    @JSExport
    public Simulator.Reason simulate() throws SimulationException {
        return this.main.simulate(-1);
    }

    @JSExport
    public Simulator.Reason simulateWithLimit(int limit) throws SimulationException {
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
    public Simulator.Reason simulateWithBreakpoints(int[] breakpoints) throws SimulationException {
        return this.main.simulate(breakpoints);
    }

    @JSExport
    public Simulator.Reason simulateWithBreakpointsAndLimit(int[] breakpoints, int limit) throws SimulationException {
        return this.main.simulate(limit, breakpoints);
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
    public static void is64Bit() {
        RARS.is64Bit();
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
        return RARS.getInstructionSet().getInstructionList().stream().map(JsInstruction::new).toArray(JsInstruction[]::new);
    }

    @JSExport
    public void setRegisterValue(String register, int value) {
        RegisterFile.getRegister(register).setValue(value);
    }

    @JSProperty
    @JSExport
    public boolean terminated() {
        return this.main.hasTerminated();
    }
}
