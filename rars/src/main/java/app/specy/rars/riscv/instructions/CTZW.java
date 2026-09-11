package app.specy.rars.riscv.instructions;

public class CTZW extends BitManipulation {
    public CTZW() {
        super("ctzw t1,t2",
                "Count trailing zeros (32 bit) : Set t1 to the number of 0 bits below the least significant 1 bit of the low 32 bits of t2, which is 32 when they are zero",
                "0110000", "00001", "001", "0011011", true);
    }

    public long compute(long value) {
        return Integer.numberOfTrailingZeros((int) value);
    }
}
