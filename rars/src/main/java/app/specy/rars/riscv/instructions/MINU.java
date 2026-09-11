package app.specy.rars.riscv.instructions;

public class MINU extends Arithmetic {
    public MINU() {
        super("minu t1,t2,t3",
                "Minimum (unsigned) : Set t1 to the smaller of t2 and t3, comparing as unsigned",
                "0000101", "101");
    }

    public long compute(long value, long value2) {
        return Long.compareUnsigned(value, value2) <= 0 ? value : value2;
    }

    protected int computeW(int value, int value2) {
        return Integer.compareUnsigned(value, value2) <= 0 ? value : value2;
    }
}
