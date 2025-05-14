package app.specy.rars;

import java.util.List;

import app.specy.rars.assembler.SymbolTable;
import app.specy.rars.assembler.TokenList;
import app.specy.rars.riscv.fs.RISCVFileSystem;
import app.specy.rars.riscv.fs.MemoryFileSystem;
import app.specy.rars.riscv.hardware.*;
import app.specy.rars.riscv.instructions.Instruction;
import app.specy.rars.riscv.instructions.InstructionSet;
import app.specy.rars.riscv.instructions.SyscallLoader;
import app.specy.rars.riscv.io.RISCVIO;
import app.specy.rars.simulator.Simulator;
import app.specy.rars.util.SystemIO;

public class RARS {

    private List<RISCVprogram> programs;
    private RISCVprogram main;
    private static RISCVIO io;
    private boolean terminated = false;


    public static void setIo(RISCVIO io) {
        RARS.io = io;
        Globals.instructionSet.setSyscallLoader(new SyscallLoader(io));
        SystemIO.setRISCVIO(io);
    }

    public ProgramStatement getAddressFromSourceLine(int line) {
        List<ProgramStatement> statements = this.main.getParsedList();
        for(ProgramStatement statement : statements) {
            if(statement.getSourceLine() == line) {
                return statement;
            }
        }
        return null;
    }

    public List<TokenList> getTokens(){
        return this.main.getTokenList();
    }

    public ProgramStatement getStatementAtAddress(int address) {
        return this.main.getMachineStatement(address);
    }

    public List<ProgramStatement> getParsedStatements() {
        return this.main.getParsedList();
    }

    public List<ProgramStatement> getStatements() {
        return this.main.getMachineList();
    }

    public RISCV(RISCVprogram main, List<RISCVprogram> programs) {
        this.programs = programs;
        this.main = main;
    }

    public static RISCV fromFs(String main, RISCVFileSystem files) throws ProcessingException {
        RISCVprogram mainProgram = new RISCVprogram();
        List<RISCVprogram> programs = mainProgram.prepareFilesForAssembly(main, files, null);
        return new RISCV(mainProgram, programs);
    }

    public static RISCV fromSource(String source) throws ProcessingException {
        RISCVFileSystem files = new MemoryFileSystem();
        files.write("main", source);
        return RISCV.fromFs("main", files);
    }

    public static void initializeRISCV() {
        Globals.initialize();
    }

    public ErrorList assemble() throws ProcessingException {
        ErrorList result = this.main.assemble(this.programs, true);
        Globals.program = this.main;
        return result;
    }

    public void initialize(boolean startAtMain) {
        RegisterFile.resetRegisters();
        Coprocessor0.resetRegisters();
        Coprocessor1.resetRegisters();
        RegisterFile.initializeProgramCounter(startAtMain);
        Stack.clearCallStack();
        terminated = false;
    }

    public StackFrame[] getCallStack(){
        StackFrame[] stack = new StackFrame[Stack.getCallStack().size()];
        for(int i = 0; i < stack.length; i++) {
            stack[i] = Stack.getCallStack().get(i);
        }
        return stack;
    }

    public String getLabelAtAddress(int address){
        return this.main.getLocalSymbolTable().getSymbolGivenIntAddress(address).getName();
    }

    public boolean simulate(int[] breakpoints) throws ProcessingException {
        terminated = this.main.simulate(breakpoints);
        return terminated;
    }
    public boolean simulate(int limit) throws ProcessingException {
        terminated = this.main.simulate(limit);
        return terminated;
    }
    public boolean simulate(int[] breakpoints, int limit) throws ProcessingException {
        terminated = this.main.simulateFromPC(breakpoints, limit);
        return terminated;
    }

    public boolean step() throws ProcessingException {
        terminated = this.main.simulateStepAtPC();
        return terminated;
    }

    public RISCVprogram getProgram() {
        return this.main;
    }

    public static InstructionSet getInstructionSet() {
        if(Globals.instructionSet == null) {
            initializeRISCV();
        }
        return Globals.getInstructionSet();
    }

    public boolean hasTerminated(){
        return terminated;
    }

    public Simulator getSimulator() {
        return Simulator.getInstance();
    }

}
