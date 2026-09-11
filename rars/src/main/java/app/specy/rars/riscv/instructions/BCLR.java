package app.specy.rars.riscv.instructions;

public class BCLR extends Arithmetic {
    public BCLR() {
        super("bclr t1,t2,t3",
                "Clear bit : Set t1 to t2 with the single bit numbered by the low bits of t3 cleared",
                "0100100", "001");
    }

    public long compute(long value, long value2) {
        return value & ~(1L << (value2 & 63));
    }

    protected int computeW(int value, int value2) {
        return value & ~(1 << (value2 & 31));
    }
}
