package app.specy.rars.riscv.instructions;

public class SH3ADDUW extends Arithmetic {
    public SH3ADDUW() {
        super("sh3add.uw t1,t2,t3",
                "Shift unsigned word left by 3 and add : Set t1 to (zero extended low 32 bits of t2 << 3) + t3",
                "0010000", "110", true);
    }

    public long compute(long value, long value2) {
        return ((value & 0xFFFFFFFFL) << 3) + value2;
    }
}
