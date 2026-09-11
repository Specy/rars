package app.specy.rars.riscv.instructions;

public class MAXU extends Arithmetic {
    public MAXU() {
        super("maxu t1,t2,t3",
                "Maximum (unsigned) : Set t1 to the larger of t2 and t3, comparing as unsigned",
                "0000101", "111");
    }

    public long compute(long value, long value2) {
        return Long.compareUnsigned(value, value2) >= 0 ? value : value2;
    }

    protected int computeW(int value, int value2) {
        return Integer.compareUnsigned(value, value2) >= 0 ? value : value2;
    }
}
