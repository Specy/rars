package app.specy.rars.riscv.instructions;

import app.specy.rars.ProgramStatement;
import app.specy.rars.SimulationException;
import app.specy.rars.jsoftfloat.Environment;
import app.specy.rars.jsoftfloat.types.Float32;
import app.specy.rars.riscv.BasicInstruction;
import app.specy.rars.riscv.BasicInstructionFormat;
import app.specy.rars.riscv.hardware.FloatingPointRegisterFile;
import app.specy.rars.riscv.hardware.RegisterFile;

public class FCVTLUS extends BasicInstruction {
    public FCVTLUS() {
        super("fcvt.lu.s t1, f1, dyn", "Convert unsigned 64 bit integer from float: Assigns the value of f1 (rounded) to t1",
                BasicInstructionFormat.I_FORMAT, "1100000 00011 sssss ttt fffff 1010011",true);
    }

    public void simulate(ProgramStatement statement) throws SimulationException {
        int[] operands = statement.getOperands();
        Environment e = new Environment();
        e.mode = Floating.getRoundingMode(operands[2],statement);
        Float32 in = new Float32(FloatingPointRegisterFile.getValue(operands[1]));
        long out = app.specy.rars.jsoftfloat.operations.Conversions.convertToUnsignedLong(in,e,false);
        Floating.setfflags(e);
        RegisterFile.updateRegister(operands[0],out);
    }
}