package app.specy.rars.riscv.instructions;

public class SH1ADDUW extends Arithmetic {
    public SH1ADDUW() {
        super("sh1add.uw t1,t2,t3",
                "Shift unsigned word left by 1 and add : Set t1 to (zero extended low 32 bits of t2 << 1) + t3",
                "0010000", "010", true);
    }

    public long compute(long value, long value2) {
        return ((value & 0xFFFFFFFFL) << 1) + value2;
    }
}
