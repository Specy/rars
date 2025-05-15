package app.specy.rars.riscv.instructions;

import app.specy.rars.jsoftfloat.Environment;
import app.specy.rars.jsoftfloat.types.Float64;

public class FADDD extends Double{
    public FADDD() {
        super("fadd.d", "Floating ADD (64 bit): assigns f1 to f2 + f3", "0000001");
    }

    @Override
    public Float64 compute(Float64 f1, Float64 f2, Environment e) {
        return app.specy.rars.jsoftfloat.operations.Arithmetic.add(f1,f2,e);
    }
}