package app.specy.rars.riscv.instructions;

public class AMOSWAPW extends Atomic {
    public AMOSWAPW() {
        super("amoswap.w t1, t2, (t3)",
                "Atomic swap : Set t1 to the word at the address in t3, and store t2 there. The exchange is indivisible, which makes this the natural way to implement a spinlock",
                "00001", "010");
    }

    public long compute(long loaded, long value) {
        return value;
    }
}
