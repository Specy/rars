package app.specy.rars.riscv.instructions;

import app.specy.rars.jsoftfloat.Environment;
import app.specy.rars.jsoftfloat.types.Float64;

public class FDIVD extends Double{
    public FDIVD() {
        super("fdiv.d", "Floating DIVide (64 bit): assigns f1 to f2 / f3", "0001101");
    }

    @Override
    public Float64 compute(Float64 f1, Float64 f2, Environment e) {
        return jsoftfloat.operations.Arithmetic.division(f1,f2,e);
    }
}
