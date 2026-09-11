package app.specy.rars.riscv.instructions;

import app.specy.rars.Globals;
import app.specy.rars.ProgramStatement;
import app.specy.rars.SimulationException;
import app.specy.rars.riscv.BasicInstruction;
import app.specy.rars.riscv.BasicInstructionFormat;
import app.specy.rars.riscv.hardware.AddressErrorException;
import app.specy.rars.riscv.hardware.RegisterFile;
import app.specy.rars.riscv.hardware.ReservationTable;

/**
 * Base class for the atomic memory operations of the A extension.
 * <p>
 * An AMO reads the word (or double word) at the address in rs1, combines it
 * with rs2, writes the combined value back, and leaves the <em>original</em>
 * memory value in rd. The whole sequence is indivisible; on a single hart that
 * is automatic, so the only thing worth enforcing is natural alignment, which
 * the memory accessors already do.
 */
public abstract class Atomic extends BasicInstruction {
    protected final boolean doubleWord;

    public Atomic(String usage, String description, String funct5, String funct3) {
        super(usage, description, BasicInstructionFormat.R_FORMAT,
                funct5 + "00 sssss ttttt " + funct3 + " fffff 0101111");
        this.doubleWord = funct3.equals("011");
    }

    public Atomic(String usage, String description, String funct5, String funct3, boolean rv64) {
        super(usage, description, BasicInstructionFormat.R_FORMAT,
                funct5 + "00 sssss ttttt " + funct3 + " fffff 0101111", rv64);
        this.doubleWord = funct3.equals("011");
    }

    public void simulate(ProgramStatement statement) throws SimulationException {
        int[] operands = statement.getOperands();
        int address = RegisterFile.getValue(operands[2]);
        try {
            if (doubleWord) {
                long loaded = Globals.memory.getDoubleWord(address);
                Globals.memory.setDoubleWord(address, compute(loaded, RegisterFile.getValueLong(operands[1])));
                ReservationTable.invalidate(address);
                RegisterFile.updateRegister(operands[0], loaded);
            } else {
                int loaded = Globals.memory.getWord(address);
                Globals.memory.setWord(address, computeW(loaded, RegisterFile.getValue(operands[1])));
                ReservationTable.invalidate(address);
                RegisterFile.updateRegister(operands[0], loaded);
            }
        } catch (AddressErrorException e) {
            throw new SimulationException(statement, e);
        }
    }

    /**
     * @param loaded the value currently in memory
     * @param value  the value from rs2
     * @return the value to write back to memory
     */
    protected abstract long compute(long loaded, long value);

    /**
     * The 32 bit version, used by the .w instructions. Override when truncating
     * the 64 bit result is not the correct answer, as it is not for the
     * unsigned min/max.
     */
    protected int computeW(int loaded, int value) {
        return (int) compute(loaded, value);
    }
}
