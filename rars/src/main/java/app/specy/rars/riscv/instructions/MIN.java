package app.specy.rars.riscv.instructions;

public class MIN extends Arithmetic {
    public MIN() {
        super("min t1,t2,t3",
                "Minimum (signed) : Set t1 to the smaller of t2 and t3, comparing as signed. Branch free, unlike the usual blt and move",
                "0000101", "100");
    }

    public long compute(long value, long value2) {
        return Math.min(value, value2);
    }
}
