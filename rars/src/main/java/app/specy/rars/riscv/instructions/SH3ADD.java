package app.specy.rars.riscv.instructions;

public class SH3ADD extends Arithmetic {
    public SH3ADD() {
        super("sh3add t1,t2,t3",
                "Shift left by 3 and add : Set t1 to (t2 << 3) + t3. One instruction for indexing an array of double words",
                "0010000", "110");
    }

    public long compute(long value, long value2) {
        return (value << 3) + value2;
    }
}
