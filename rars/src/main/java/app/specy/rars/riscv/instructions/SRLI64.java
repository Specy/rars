package app.specy.rars.riscv.instructions;

import app.specy.rars.ProgramStatement;
import app.specy.rars.riscv.BasicInstruction;
import app.specy.rars.riscv.BasicInstructionFormat;
import app.specy.rars.riscv.hardware.RegisterFile;

public class SRLI64 extends BasicInstruction {
    public SRLI64(){
        super("srli t1,t2,33", "Shift right logical : Set t1 to result of shifting t2 right by number of bits specified by immediate",
                BasicInstructionFormat.R_FORMAT, "000000 tttttt sssss 101 fffff 0010011",true);
    }

    public void simulate(ProgramStatement statement) {
        int[] operands = statement.getOperands();
        RegisterFile.updateRegister(operands[0], RegisterFile.getValueLong(operands[1]) >>> operands[2]);
    }
}
