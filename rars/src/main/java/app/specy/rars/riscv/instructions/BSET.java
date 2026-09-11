package app.specy.rars.riscv.instructions;

public class BSET extends Arithmetic {
    public BSET() {
        super("bset t1,t2,t3",
                "Set bit : Set t1 to t2 with the single bit numbered by the low bits of t3 set",
                "0010100", "001");
    }

    public long compute(long value, long value2) {
        return value | (1L << (value2 & 63));
    }

    protected int computeW(int value, int value2) {
        return value | (1 << (value2 & 31));
    }
}
