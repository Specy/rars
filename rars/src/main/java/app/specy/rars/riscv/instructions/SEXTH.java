package app.specy.rars.riscv.instructions;

public class SEXTH extends BitManipulation {
    public SEXTH() {
        super("sext.h t1,t2",
                "Sign extend half word : Set t1 to the low 16 bits of t2 sign extended to the full register width",
                "0110000", "00101", "001");
    }

    public long compute(long value) {
        return (short) value;
    }
}
