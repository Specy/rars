package app.specy.rars.riscv.instructions;

import app.specy.rars.ProgramStatement;
import app.specy.rars.riscv.BasicInstruction;
import app.specy.rars.riscv.BasicInstructionFormat;
import app.specy.rars.riscv.hardware.FloatingPointRegisterFile;
import app.specy.rars.riscv.hardware.RegisterFile;

public class FMVDX extends BasicInstruction {
    public FMVDX() {
        super("fmv.d.x f1, t1", "Move float: move bits representing a double from an 64 bit integer register",
                BasicInstructionFormat.I_FORMAT, "1111001 00000 sssss 000 fffff 1010011",true);
    }

    public void simulate(ProgramStatement statement) {
        int[] operands = statement.getOperands();
        FloatingPointRegisterFile.updateRegisterLong(operands[0], RegisterFile.getValueLong(operands[1]));
    }
}