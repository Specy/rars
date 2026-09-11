package app.specy.rars.riscv.instructions;

public class CPOP extends BitManipulation {
    public CPOP() {
        super("cpop t1,t2",
                "Count set bits : Set t1 to the number of 1 bits in t2, also known as the population count",
                "0110000", "00010", "001");
    }

    public long compute(long value) {
        return Long.bitCount(value);
    }

    protected int computeW(int value) {
        return Integer.bitCount(value);
    }
}
