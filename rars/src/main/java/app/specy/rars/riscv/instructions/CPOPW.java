package app.specy.rars.riscv.instructions;

public class CPOPW extends BitManipulation {
    public CPOPW() {
        super("cpopw t1,t2",
                "Count set bits (32 bit) : Set t1 to the number of 1 bits in the low 32 bits of t2",
                "0110000", "00010", "001", "0011011", true);
    }

    public long compute(long value) {
        return Integer.bitCount((int) value);
    }
}
