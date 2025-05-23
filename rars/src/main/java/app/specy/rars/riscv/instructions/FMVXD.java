package app.specy.rars.riscv.instructions;

import app.specy.rars.ProgramStatement;
import app.specy.rars.riscv.BasicInstruction;
import app.specy.rars.riscv.BasicInstructionFormat;
import app.specy.rars.riscv.hardware.FloatingPointRegisterFile;
import app.specy.rars.riscv.hardware.RegisterFile;

public class FMVXD extends BasicInstruction {
    public FMVXD() {
        super("fmv.x.d t1, f1", "Move double: move bits representing a double to an 64 bit integer register",
                BasicInstructionFormat.I_FORMAT, "1110001 00000 sssss 000 fffff 1010011",true);
    }

    public void simulate(ProgramStatement statement) {
        int[] operands = statement.getOperands();
        RegisterFile.updateRegister(operands[0], FloatingPointRegisterFile.getValueLong(operands[1]));
    }
}