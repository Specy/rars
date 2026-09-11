package app.specy.rars.riscv.instructions;

public class SH2ADDUW extends Arithmetic {
    public SH2ADDUW() {
        super("sh2add.uw t1,t2,t3",
                "Shift unsigned word left by 2 and add : Set t1 to (zero extended low 32 bits of t2 << 2) + t3",
                "0010000", "100", true);
    }

    public long compute(long value, long value2) {
        return ((value & 0xFFFFFFFFL) << 2) + value2;
    }
}
