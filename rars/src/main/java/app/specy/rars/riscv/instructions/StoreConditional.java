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
 * Base class for sc.w and sc.d. The store only happens when a reservation from
 * a matching load-reserved is still held; rd reports 0 on success and a nonzero
 * value on failure, so the caller is expected to retry the whole sequence.
 */
public abstract class StoreConditional extends BasicInstruction {
    private static final int SUCCESS = 0;
    private static final int FAILURE = 1;

    private final boolean doubleWord;

    public StoreConditional(String usage, String description, String funct3) {
        super(usage, description, BasicInstructionFormat.R_FORMAT,
                "0001100 sssss ttttt " + funct3 + " fffff 0101111");
        this.doubleWord = funct3.equals("011");
    }

    public StoreConditional(String usage, String description, String funct3, boolean rv64) {
        super(usage, description, BasicInstructionFormat.R_FORMAT,
                "0001100 sssss ttttt " + funct3 + " fffff 0101111", rv64);
        this.doubleWord = funct3.equals("011");
    }

    public void simulate(ProgramStatement statement) throws SimulationException {
        int[] operands = statement.getOperands();
        int address = RegisterFile.getValue(operands[2]);

        // A misaligned address is an error whether or not the reservation holds,
        // so check before deciding the outcome.
        if ((address & 3) != 0) {
            throw new SimulationException(statement, new AddressErrorException(
                    "Store address not aligned to word boundary ",
                    SimulationException.STORE_ADDRESS_MISALIGNED, address));
        }

        boolean reserved = ReservationTable.isReserved(address);
        ReservationTable.invalidate(address);
        if (!reserved) {
            RegisterFile.updateRegister(operands[0], FAILURE);
            return;
        }

        try {
            if (doubleWord) {
                Globals.memory.setDoubleWord(address, RegisterFile.getValueLong(operands[1]));
            } else {
                Globals.memory.setWord(address, RegisterFile.getValue(operands[1]));
            }
        } catch (AddressErrorException e) {
            throw new SimulationException(statement, e);
        }
        RegisterFile.updateRegister(operands[0], SUCCESS);
    }
}
