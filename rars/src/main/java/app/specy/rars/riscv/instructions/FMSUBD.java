package app.specy.rars.riscv.instructions;

import app.specy.rars.jsoftfloat.Environment;
import app.specy.rars.jsoftfloat.types.Float64;

public class FMSUBD extends FusedDouble {
    public FMSUBD() {
        super("fmsub.d f1, f2, f3, f4", "Fused Multiply Subatract: Assigns f2*f3-f4 to f1", "01");
    }

    public Float64 compute(Float64 f1, Float64 f2, Float64 f3, Environment e){
        return app.specy.rars.jsoftfloat.operations.Arithmetic.fusedMultiplyAdd(f1,f2,f3.negate(),e);
    }
}
