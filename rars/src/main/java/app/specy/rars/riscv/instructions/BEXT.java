package app.specy.rars.riscv.instructions;

public class BEXT extends Arithmetic {
    public BEXT() {
        super("bext t1,t2,t3",
                "Extract bit : Set t1 to the single bit of t2 numbered by the low bits of t3, as 0 or 1",
                "0100100", "101");
    }

    public long compute(long value, long value2) {
        return (value >>> (value2 & 63)) & 1;
    }

    protected int computeW(int value, int value2) {
        return (value >>> (value2 & 31)) & 1;
    }
}
