package app.specy.rars.riscv.instructions;

import app.specy.rars.jsoftfloat.Environment;
import app.specy.rars.jsoftfloat.types.Float32;
import app.specy.rars.jsoftfloat.types.Float64;
import app.specy.rars.ProgramStatement;
import app.specy.rars.SimulationException;
import app.specy.rars.riscv.BasicInstruction;
import app.specy.rars.riscv.BasicInstructionFormat;
import app.specy.rars.riscv.hardware.FloatingPointRegisterFile;

public class FCVTDS extends BasicInstruction {
    public FCVTDS() {
        super("fcvt.d.s f1, f2, dyn", "Convert a float to a double: Assigned the value of f2 to f1",
                BasicInstructionFormat.R4_FORMAT,"0100001 00000 sssss ttt fffff 1010011");
    }

    public void simulate(ProgramStatement statement) throws SimulationException {
        int[] operands = statement.getOperands();
        Environment e = new Environment();
        e.mode = Floating.getRoundingMode(operands[2],statement);
        Float32 in = new Float32(FloatingPointRegisterFile.getValue(operands[1]));
        Float64 out = new Float64(0);
        out = FCVTSD.convert(in,out,e);
        Floating.setfflags(e);
        FloatingPointRegisterFile.updateRegisterLong(operands[0],out.bits);
    }
}