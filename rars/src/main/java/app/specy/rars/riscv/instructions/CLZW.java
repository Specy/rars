package app.specy.rars.riscv.instructions;

public class CLZW extends BitManipulation {
    public CLZW() {
        super("clzw t1,t2",
                "Count leading zeros (32 bit) : Set t1 to the number of 0 bits above the most significant 1 bit of the low 32 bits of t2, which is 32 when they are zero",
                "0110000", "00000", "001", "0011011", true);
    }

    public long compute(long value) {
        return Integer.numberOfLeadingZeros((int) value);
    }
}
