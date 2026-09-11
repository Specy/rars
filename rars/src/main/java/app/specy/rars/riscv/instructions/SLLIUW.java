package app.specy.rars.riscv.instructions;

import app.specy.rars.ProgramStatement;
import app.specy.rars.riscv.BasicInstruction;
import app.specy.rars.riscv.BasicInstructionFormat;
import app.specy.rars.riscv.hardware.RegisterFile;

public class SLLIUW extends BasicInstruction {
    public SLLIUW() {
        super("slli.uw t1,t2,10",
                "Shift unsigned word left by immediate : Set t1 to the low 32 bits of t2, zero extended, shifted left by the immediate. Scales an unsigned 32 bit index without a separate zero extension",
                BasicInstructionFormat.R_FORMAT, "000010 tttttt sssss 001 fffff 0011011", true);
    }

    public void simulate(ProgramStatement statement) {
        int[] operands = statement.getOperands();
        RegisterFile.updateRegister(operands[0],
                (RegisterFile.getValueLong(operands[1]) & 0xFFFFFFFFL) << operands[2]);
    }
}
