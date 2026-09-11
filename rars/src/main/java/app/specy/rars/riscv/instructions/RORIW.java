package app.specy.rars.riscv.instructions;

import app.specy.rars.ProgramStatement;
import app.specy.rars.riscv.BasicInstruction;
import app.specy.rars.riscv.BasicInstructionFormat;
import app.specy.rars.riscv.hardware.RegisterFile;

public class RORIW extends BasicInstruction {
    public RORIW() {
        super("roriw t1,t2,10",
                "Rotate right by immediate (32 bit) : Set t1 to the low 32 bits of t2 rotated right by the immediate, sign extended",
                BasicInstructionFormat.R_FORMAT, "0110000 ttttt sssss 101 fffff 0011011", true);
    }

    public void simulate(ProgramStatement statement) {
        int[] operands = statement.getOperands();
        RegisterFile.updateRegister(operands[0],
                Integer.rotateRight(RegisterFile.getValue(operands[1]), operands[2] & 31));
    }
}
