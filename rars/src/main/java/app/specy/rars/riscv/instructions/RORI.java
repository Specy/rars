package app.specy.rars.riscv.instructions;

import app.specy.rars.ProgramStatement;
import app.specy.rars.riscv.BasicInstruction;
import app.specy.rars.riscv.BasicInstructionFormat;
import app.specy.rars.riscv.InstructionSet;
import app.specy.rars.riscv.hardware.RegisterFile;

public class RORI extends BasicInstruction {
    public RORI() {
        super(InstructionSet.rv64 ? "rori t1,t2,33" : "rori t1,t2,10",
                "Rotate right by immediate : Set t1 to t2 rotated right by the immediate, so bits shifted off the bottom re-enter at the top",
                BasicInstructionFormat.R_FORMAT,
                // rv64 takes a sixth shift amount bit from the function code
                InstructionSet.rv64
                        ? "011000 tttttt sssss 101 fffff 0010011"
                        : "0110000 ttttt sssss 101 fffff 0010011");
    }

    public void simulate(ProgramStatement statement) {
        int[] operands = statement.getOperands();
        if (InstructionSet.rv64) {
            RegisterFile.updateRegister(operands[0],
                    Long.rotateRight(RegisterFile.getValueLong(operands[1]), operands[2]));
        } else {
            RegisterFile.updateRegister(operands[0],
                    Integer.rotateRight(RegisterFile.getValue(operands[1]), operands[2]));
        }
    }
}
