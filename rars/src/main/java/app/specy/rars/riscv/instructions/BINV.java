package app.specy.rars.riscv.instructions;

public class BINV extends Arithmetic {
    public BINV() {
        super("binv t1,t2,t3",
                "Invert bit : Set t1 to t2 with the single bit numbered by the low bits of t3 flipped",
                "0110100", "001");
    }

    public long compute(long value, long value2) {
        return value ^ (1L << (value2 & 63));
    }

    protected int computeW(int value, int value2) {
        return value ^ (1 << (value2 & 31));
    }
}
