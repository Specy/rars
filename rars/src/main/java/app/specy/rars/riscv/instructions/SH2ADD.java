package app.specy.rars.riscv.instructions;

public class SH2ADD extends Arithmetic {
    public SH2ADD() {
        super("sh2add t1,t2,t3",
                "Shift left by 2 and add : Set t1 to (t2 << 2) + t3. One instruction for indexing an array of words",
                "0010000", "100");
    }

    public long compute(long value, long value2) {
        return (value << 2) + value2;
    }
}
