package app.specy.rars.riscv.instructions;

import app.specy.rars.jsoftfloat.Environment;
import app.specy.rars.jsoftfloat.types.Float64;

public class FSUBD extends Double {
    public FSUBD() {
        super("fsub.d", "Floating SUBtract (64 bit): assigns f1 to f2 - f3", "0000101");
    }

    @Override
    public Float64 compute(Float64 f1, Float64 f2, Environment e) {
        return app.specy.rars.jsoftfloat.operations.Arithmetic.subtraction(f1,f2,e);
    }
}
