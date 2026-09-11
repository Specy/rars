package app.specy.rars.riscv.instructions;

public class SH1ADD extends Arithmetic {
    public SH1ADD() {
        super("sh1add t1,t2,t3",
                "Shift left by 1 and add : Set t1 to (t2 << 1) + t3. One instruction for stepping a pointer through an array of 2 byte elements",
                "0010000", "010");
    }

    public long compute(long value, long value2) {
        return (value << 1) + value2;
    }
}
