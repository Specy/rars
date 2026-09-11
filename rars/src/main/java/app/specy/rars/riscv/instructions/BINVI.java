package app.specy.rars.riscv.instructions;

import app.specy.rars.ProgramStatement;
import app.specy.rars.riscv.BasicInstruction;
import app.specy.rars.riscv.BasicInstructionFormat;
import app.specy.rars.riscv.InstructionSet;
import app.specy.rars.riscv.hardware.RegisterFile;

public class BINVI extends BasicInstruction {
    public BINVI() {
        super(InstructionSet.rv64 ? "binvi t1,t2,33" : "binvi t1,t2,10",
                "Invert bit by immediate : Set t1 to t2 with the single bit numbered by the immediate flipped",
                BasicInstructionFormat.R_FORMAT,
                // rv64 takes a sixth bit for the immediate from the function code
                InstructionSet.rv64
                        ? "011010 tttttt sssss 001 fffff 0010011"
                        : "0110100 ttttt sssss 001 fffff 0010011");
    }

    public void simulate(ProgramStatement statement) {
        int[] operands = statement.getOperands();
        int shamt = operands[2];
        if (InstructionSet.rv64) {
            long value = RegisterFile.getValueLong(operands[1]);
            RegisterFile.updateRegister(operands[0], value ^ (1L << shamt));
        } else {
            int value = RegisterFile.getValue(operands[1]);
            RegisterFile.updateRegister(operands[0], value ^ (1 << shamt));
        }
    }
}
