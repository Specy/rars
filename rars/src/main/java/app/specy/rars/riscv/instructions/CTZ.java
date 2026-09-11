package app.specy.rars.riscv.instructions;

public class CTZ extends BitManipulation {
    public CTZ() {
        super("ctz t1,t2",
                "Count trailing zeros : Set t1 to the number of 0 bits below the least significant 1 bit of t2, which is the full register width when t2 is zero",
                "0110000", "00001", "001");
    }

    public long compute(long value) {
        return Long.numberOfTrailingZeros(value);
    }

    protected int computeW(int value) {
        return Integer.numberOfTrailingZeros(value);
    }
}
