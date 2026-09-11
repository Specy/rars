package app.specy.rars.riscv.instructions;

public class MAX extends Arithmetic {
    public MAX() {
        super("max t1,t2,t3",
                "Maximum (signed) : Set t1 to the larger of t2 and t3, comparing as signed. Branch free, unlike the usual blt and move",
                "0000101", "110");
    }

    public long compute(long value, long value2) {
        return Math.max(value, value2);
    }
}
