package app.specy.rars.riscv.instructions;

public class ANDN extends Arithmetic {
    public ANDN() {
        super("andn t1,t2,t3",
                "AND with inverted operand : Set t1 to the bitwise AND of t2 and the bitwise NOT of t3. Clears in t2 every bit that is set in t3",
                "0100000", "111");
    }

    public long compute(long value, long value2) {
        return value & ~value2;
    }
}
