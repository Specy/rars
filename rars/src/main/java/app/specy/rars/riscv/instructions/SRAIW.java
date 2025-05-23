package app.specy.rars.riscv.instructions;

import app.specy.rars.ProgramStatement;
import app.specy.rars.riscv.BasicInstruction;
import app.specy.rars.riscv.BasicInstructionFormat;
import app.specy.rars.riscv.hardware.RegisterFile;

public class SRAIW extends BasicInstruction {
    public SRAIW() {
        super("sraiw t1,t2,10", "Shift right arithmetic (32 bit): Set t1 to result of sign-extended shifting t2 right by number of bits specified by immediate",
                BasicInstructionFormat.R_FORMAT, "0100000 ttttt sssss 101 fffff 0011011",true);
    }

    public void simulate(ProgramStatement statement) {
        // Use the code directly from SRAI
        int[] operands = statement.getOperands();
        RegisterFile.updateRegister(operands[0], RegisterFile.getValue(operands[1]) >> operands[2]);
    }
}
