package app.specy.rars;

import app.specy.rars.assembler.TokenList;
import app.specy.rars.assembler.SourceLine;
import app.specy.rars.riscv.InstructionSet;
import app.specy.rars.riscv.fs.MemoryFileSystem;
import app.specy.rars.riscv.fs.RISCVFileSystem;
import app.specy.rars.riscv.fs.SourcePath;
import app.specy.rars.riscv.hardware.*;
import app.specy.rars.riscv.io.RISCVIO;
import app.specy.rars.simulator.Simulator;
import app.specy.rars.util.SystemIO;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RARS {

    private RISCVprogram main;
    private final String entryFile;
    private final MemoryFileSystem files;
    private boolean assemblyAttempted;
    private boolean assembled;
    private static RISCVIO io;
    private Simulator.Reason stopReason = null;


    public static void setIo(RISCVIO io) {
        RARS.io = io;
        Globals.instructionSet.setSyscallLoaderIO(io);
        SystemIO.setRISCVIO(io);
    }

    public List<TokenList> getTokens(){
        requireTokenized();
        return this.main.getTokenList();
    }

    public List<SourceLine> getSourceLines() {
        requireTokenized();
        return this.main.getSourceLineList();
    }

    public ProgramStatement getStatementAtAddress(int address) {
        requireAssembled();
        return this.main.getMachineStatement(address);
    }

    public List<ProgramStatement> getParsedStatements() {
        requireAssembled();
        return this.main.getParsedList();
    }

    public List<ProgramStatement> getStatements() {
        requireAssembled();
        return this.main.getMachineList();
    }

    public List<ProgramStatement> getStatementsAtSourceLocation(String sourcePath, int sourceLine) {
        requireAssembled();
        SourcePath.requireCanonical(sourcePath);
        if (sourceLine < 1) {
            throw new IllegalArgumentException("Source line must be a positive integer");
        }
        return this.main.getMachineList().stream()
                .filter(statement -> statement.getSourcePath().equals(sourcePath)
                        && statement.getSourceLine() == sourceLine)
                .toList();
    }

    private RARS(String entryFile, MemoryFileSystem files) {
        this.entryFile = entryFile;
        this.files = files;
    }

    public static RARS fromFs(String entryFile, RISCVFileSystem sourceFiles) {
        SourcePath.requireCanonical(entryFile);
        if (sourceFiles == null) {
            throw new IllegalArgumentException("Source set must be an object");
        }

        MemoryFileSystem snapshot = new MemoryFileSystem();
        Set<String> paths = new HashSet<>();
        for (RISCVFile file : sourceFiles.getFiles()) {
            if (file == null) {
                throw new IllegalArgumentException("Source set must not contain null files");
            }
            String path = SourcePath.requireCanonical(file.getName());
            if (!paths.add(path)) {
                throw new IllegalArgumentException("Duplicate source path: " + path);
            }
            if (file.getSource() == null) {
                throw new IllegalArgumentException("Source content must be a string: " + path);
            }
            snapshot.write(path, file.getSource());
        }
        if (!paths.contains(entryFile)) {
            throw new IllegalArgumentException("Entry file is not present in the source set: " + entryFile);
        }
        return new RARS(entryFile, snapshot);
    }

    public static void initializeRISCV() {
        Globals.initialize();
    }

    public ErrorList assemble() throws AssemblyException {
        assemblyAttempted = true;
        assembled = false;
        stopReason = null;
        Globals.program = null;
        Globals.symbolTable.clear();
        Globals.memory.clear();
        main = new RISCVprogram();
        main.prepareForAssembly(entryFile, files);
        ErrorList result = main.assemble(new java.util.ArrayList<>(List.of(main)), true);
        Globals.program = main;
        assembled = true;
        return result;
    }

    public void initialize(boolean startAtMain) {
        requireAssembled();
        RegisterFile.resetRegisters();
        FloatingPointRegisterFile.resetRegisters();
        ControlAndStatusRegisterFile.resetRegisters();
        InterruptController.reset();
        ReservationTable.reset();
        RegisterFile.initializeProgramCounter(true);
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
        requireAssembled();
        StackFrame[] stack = new StackFrame[Stack.getCallStack().size()];
        for(int i = 0; i < stack.length; i++) {
            stack[i] = Stack.getCallStack().get(i);
        }
        return stack;
    }

    public String getLabelAtAddress(int address){
        requireAssembled();
        return this.main.getLocalSymbolTable().getSymbolGivenIntAddress(address).getName();
    }

    public Simulator.Reason simulate(int[] breakpoints) throws SimulationException {
        requireAssembled();
        stopReason = this.main.simulate(-1, breakpoints);
        return stopReason;
    }
    public Simulator.Reason simulate(int limit, int[] breakpoints) throws SimulationException {
        requireAssembled();
        stopReason = this.main.simulate(limit, breakpoints);
        return stopReason;
    }

    public Simulator.Reason simulate(int limit) throws SimulationException {
        requireAssembled();
        stopReason = this.main.simulate(limit);
        return stopReason;
    }

    public static void setIs64Bit(boolean is64Bit) {
        Globals.setIs64Bit(is64Bit);
    }

    public static boolean is64Bit(){
        return Globals.is64Bit();
    }

    public Simulator.Reason step() throws SimulationException {
        requireAssembled();
        stopReason = this.main.simulate(1);
        return stopReason;
    }

    public Simulator.Reason getStopReason() {
        return stopReason;
    }

    public RISCVprogram getProgram() {
        requireAssembled();
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

    private void requireTokenized() {
        if (!assemblyAttempted) {
            throw new IllegalStateException("Program has not been assembled");
        }
        if (main == null || main.getTokenList() == null || main.getSourceLineList() == null) {
            throw new IllegalStateException("Program tokenization did not complete");
        }
    }

    private void requireAssembled() {
        if (!assembled) {
            throw new IllegalStateException("Program has not been assembled successfully");
        }
    }

}
