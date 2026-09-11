package app.specy.rars.riscv.instructions;

import app.specy.rars.ProgramStatement;
import app.specy.rars.riscv.BasicInstruction;
import app.specy.rars.riscv.BasicInstructionFormat;
import app.specy.rars.riscv.InstructionSet;
import app.specy.rars.riscv.hardware.RegisterFile;

/**
 * Base class for the single source bit manipulation instructions of Zbb, such
 * as clz and rev8. They are encoded like an immediate instruction whose
 * immediate field is a fixed function code rather than an operand.
 */
public abstract class BitManipulation extends BasicInstruction {
    public BitManipulation(String usage, String description, String funct7, String rs2, String funct3) {
        this(usage, description, funct7, rs2, funct3, "0010011", false);
    }

    public BitManipulation(String usage, String description, String funct7, String rs2, String funct3, boolean rv64) {
        this(usage, description, funct7, rs2, funct3, "0010011", rv64);
    }

    /**
     * @param opcode "0010011" for the XLEN wide forms, "0011011" for the RV64
     *               only .w forms, which always operate on 32 bits
     */
    protected BitManipulation(String usage, String description, String funct7, String rs2, String funct3,
                              String opcode, boolean rv64) {
        super(usage, description, BasicInstructionFormat.I_FORMAT,
                funct7 + " " + rs2 + " sssss " + funct3 + " fffff " + opcode, rv64);
    }

    public void simulate(ProgramStatement statement) {
        int[] operands = statement.getOperands();
        if (InstructionSet.rv64) {
            RegisterFile.updateRegister(operands[0], compute(RegisterFile.getValueLong(operands[1])));
        } else {
            RegisterFile.updateRegister(operands[0], computeW(RegisterFile.getValue(operands[1])));
        }
    }

    /**
     * @param value the value from the source register
     * @return the result to be stored
     */
    protected abstract long compute(long value);

    /**
     * The 32 bit version. Override whenever truncating the 64 bit result is not
     * the right answer, which is the case for anything that counts bits or
     * depends on the register width.
     */
    protected int computeW(int value) {
        return (int) compute(value);
    }
}
