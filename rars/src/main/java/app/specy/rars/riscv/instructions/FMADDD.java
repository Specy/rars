package app.specy.rars.riscv.instructions;

import app.specy.rars.jsoftfloat.Environment;
import app.specy.rars.jsoftfloat.types.Float64;

public class FMADDD extends FusedDouble {
    public FMADDD() {
        super("fmadd.d f1, f2, f3, f4", "Fused Multiply Add (64 bit): Assigns f2*f3+f4 to f1", "00");
    }

    public Float64 compute(Float64 f1, Float64 f2, Float64 f3, Environment e){
        return jsoftfloat.operations.Arithmetic.fusedMultiplyAdd(f1,f2,f3,e);
    }
}
