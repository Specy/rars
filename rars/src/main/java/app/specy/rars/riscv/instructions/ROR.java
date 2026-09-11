package app.specy.rars.riscv.instructions;

public class ROR extends Arithmetic {
    public ROR() {
        super("ror t1,t2,t3",
                "Rotate right : Set t1 to t2 rotated right by the low bits of t3, so bits shifted off the bottom re-enter at the top",
                "0110000", "101");
    }

    public long compute(long value, long value2) {
        return Long.rotateRight(value, (int) (value2 & 63));
    }

    protected int computeW(int value, int value2) {
        return Integer.rotateRight(value, value2 & 31);
    }
}
