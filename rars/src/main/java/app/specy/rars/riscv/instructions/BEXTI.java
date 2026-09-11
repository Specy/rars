package app.specy.rars.riscv.instructions;

import app.specy.rars.ProgramStatement;
import app.specy.rars.riscv.BasicInstruction;
import app.specy.rars.riscv.BasicInstructionFormat;
import app.specy.rars.riscv.InstructionSet;
import app.specy.rars.riscv.hardware.RegisterFile;

public class BEXTI extends BasicInstruction {
    public BEXTI() {
        super(InstructionSet.rv64 ? "bexti t1,t2,33" : "bexti t1,t2,10",
                "Extract bit by immediate : Set t1 to the single bit of t2 numbered by the immediate, as 0 or 1",
                BasicInstructionFormat.R_FORMAT,
                // rv64 takes a sixth bit for the immediate from the function code
                InstructionSet.rv64
                        ? "010010 tttttt sssss 101 fffff 0010011"
                        : "0100100 ttttt sssss 101 fffff 0010011");
    }

    public void simulate(ProgramStatement statement) {
        int[] operands = statement.getOperands();
        int shamt = operands[2];
        if (InstructionSet.rv64) {
            long value = RegisterFile.getValueLong(operands[1]);
            RegisterFile.updateRegister(operands[0], (value >>> shamt) & 1);
        } else {
            int value = RegisterFile.getValue(operands[1]);
            RegisterFile.updateRegister(operands[0], (value >>> shamt) & 1);
        }
    }
}
