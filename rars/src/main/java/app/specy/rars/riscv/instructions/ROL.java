package app.specy.rars.riscv.instructions;

public class ROL extends Arithmetic {
    public ROL() {
        super("rol t1,t2,t3",
                "Rotate left : Set t1 to t2 rotated left by the low bits of t3, so bits shifted off the top re-enter at the bottom",
                "0110000", "001");
    }

    public long compute(long value, long value2) {
        return Long.rotateLeft(value, (int) (value2 & 63));
    }

    protected int computeW(int value, int value2) {
        return Integer.rotateLeft(value, value2 & 31);
    }
}
