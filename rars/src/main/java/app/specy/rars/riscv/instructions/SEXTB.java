package app.specy.rars.riscv.instructions;

public class SEXTB extends BitManipulation {
    public SEXTB() {
        super("sext.b t1,t2",
                "Sign extend byte : Set t1 to the low byte of t2 sign extended to the full register width",
                "0110000", "00100", "001");
    }

    public long compute(long value) {
        return (byte) value;
    }
}
