package app.specy.rars.riscv.instructions;

import app.specy.rars.ProgramStatement;
import app.specy.rars.riscv.BasicInstruction;
import app.specy.rars.riscv.BasicInstructionFormat;
import app.specy.rars.riscv.InstructionSet;
import app.specy.rars.riscv.hardware.RegisterFile;

public class ZEXTH extends BasicInstruction {
    public ZEXTH() {
        super("zext.h t1,t2",
                "Zero extend half word : Set t1 to the low 16 bits of t2 with the rest of the register cleared",
                BasicInstructionFormat.R_FORMAT,
                // the only Zbb instruction whose opcode differs between rv32 and rv64
                InstructionSet.rv64
                        ? "0000100 00000 sssss 100 fffff 0111011"
                        : "0000100 00000 sssss 100 fffff 0110011");
    }

    public void simulate(ProgramStatement statement) {
        int[] operands = statement.getOperands();
        RegisterFile.updateRegister(operands[0], RegisterFile.getValueLong(operands[1]) & 0xFFFFL);
    }
}
