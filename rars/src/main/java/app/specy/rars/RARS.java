package app.specy.rars;

import java.util.ArrayList;
import java.util.List;

import app.specy.rars.assembler.TokenList;
import app.specy.rars.riscv.fs.RISCVFileSystem;
import app.specy.rars.riscv.fs.MemoryFileSystem;
import app.specy.rars.riscv.hardware.*;
import app.specy.rars.riscv.InstructionSet;
import app.specy.rars.riscv.io.RISCVIO;
import app.specy.rars.simulator.Simulator;
import app.specy.rars.util.SystemIO;

public class RARS {

    private ArrayList<RISCVprogram> programs;
    private RISCVprogram main;
    private static RISCVIO io;
    private Simulator.Reason stopReason = null;


    public static void setIo(RISCVIO io) {
        RARS.io = io;
        Globals.instructionSet.setSyscallLoaderIO(io);
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

    public RARS(RISCVprogram main, ArrayList<RISCVprogram> programs) {
        this.programs = programs;
        this.main = main;
    }

    public static RARS fromFs(String main, RISCVFileSystem files) throws AssemblyException {
        RISCVprogram mainProgram = new RISCVprogram();
        ArrayList<RISCVprogram> programs = mainProgram.prepareFilesForAssembly(main, files, null);
        return new RARS(mainProgram, programs);
    }

    public static RARS fromSource(String source) throws AssemblyException {
        RISCVFileSystem files = new MemoryFileSystem();
        files.write("main", source);
        return RARS.fromFs("main", files);
    }

    public static void initializeRISCV() {
        Globals.initialize();
    }

    public ErrorList assemble() throws AssemblyException {
        ErrorList result = this.main.assemble(this.programs, true);
        Globals.program = this.main;
        return result;
    }

    public void initialize(boolean startAtMain) {
        int pc = RegisterFile.getProgramCounter();
        RegisterFile.resetRegisters();
        FloatingPointRegisterFile.resetRegisters();
        ControlAndStatusRegisterFile.resetRegisters();
        InterruptController.reset();
        RegisterFile.initializeProgramCounter(pc);
        Globals.exitCode = 0;

        // Copy in assembled code and arguments
        //simulation.copyFrom(assembled);
        //Memory tmpMem = Memory.swapInstance(simulation);
        //new ProgramArgumentList(args).storeProgramArguments();
        //Memory.swapInstance(tmpMem);

        //terminated = false;
        Stack.clearCallStack();
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

    public Simulator.Reason simulate(int[] breakpoints) throws SimulationException {
        stopReason = this.main.simulate(-1, breakpoints);
        return stopReason;
    }
    public Simulator.Reason simulate(int limit, int[] breakpoints) throws SimulationException {
        stopReason = this.main.simulate(limit, breakpoints);
        return stopReason;
    }

    public Simulator.Reason simulate(int limit) throws SimulationException {
        stopReason = this.main.simulate(limit);
        return stopReason;
    }

    public static void setIs64Bit(boolean is64Bit) {
        Globals.setIs64Bit(is64Bit);
    }


    public Simulator.Reason step() throws SimulationException {
        stopReason = this.main.simulate(1);
        return stopReason;
    }

    public Simulator.Reason getStopReason() {
        return stopReason;
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
        return stopReason == Simulator.Reason.CLIFF_TERMINATION;
    }

    public Simulator getSimulator() {
        return Simulator.getInstance();
    }

}
