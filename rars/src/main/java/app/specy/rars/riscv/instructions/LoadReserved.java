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
 * Base class for lr.w and lr.d. Loads from the address in rs1 and registers a
 * reservation on it, which a later store-conditional to the same address will
 * consume.
 */
public abstract class LoadReserved extends BasicInstruction {
    private final boolean doubleWord;

    public LoadReserved(String usage, String description, String funct3) {
        super(usage, description, BasicInstructionFormat.R_FORMAT,
                "0001000 00000 sssss " + funct3 + " fffff 0101111");
        this.doubleWord = funct3.equals("011");
    }

    public LoadReserved(String usage, String description, String funct3, boolean rv64) {
        super(usage, description, BasicInstructionFormat.R_FORMAT,
                "0001000 00000 sssss " + funct3 + " fffff 0101111", rv64);
        this.doubleWord = funct3.equals("011");
    }

    public void simulate(ProgramStatement statement) throws SimulationException {
        int[] operands = statement.getOperands();
        int address = RegisterFile.getValue(operands[1]);
        try {
            long loaded = doubleWord ? Globals.memory.getDoubleWord(address) : Globals.memory.getWord(address);
            ReservationTable.reserve(address);
            RegisterFile.updateRegister(operands[0], loaded);
        } catch (AddressErrorException e) {
            throw new SimulationException(statement, e);
        }
    }
}
