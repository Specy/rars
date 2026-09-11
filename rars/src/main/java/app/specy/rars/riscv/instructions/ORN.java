package app.specy.rars.riscv.instructions;

public class ORN extends Arithmetic {
    public ORN() {
        super("orn t1,t2,t3",
                "OR with inverted operand : Set t1 to the bitwise OR of t2 and the bitwise NOT of t3",
                "0100000", "110");
    }

    public long compute(long value, long value2) {
        return value | ~value2;
    }
}
