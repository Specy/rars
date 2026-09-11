package app.specy.rars.riscv.instructions;

public class CLZ extends BitManipulation {
    public CLZ() {
        super("clz t1,t2",
                "Count leading zeros : Set t1 to the number of 0 bits above the most significant 1 bit of t2, which is the full register width when t2 is zero",
                "0110000", "00000", "001");
    }

    public long compute(long value) {
        return Long.numberOfLeadingZeros(value);
    }

    protected int computeW(int value) {
        return Integer.numberOfLeadingZeros(value);
    }
}
