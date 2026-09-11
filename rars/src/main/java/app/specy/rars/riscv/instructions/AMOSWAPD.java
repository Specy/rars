package app.specy.rars.riscv.instructions;

public class AMOSWAPD extends Atomic {
    public AMOSWAPD() {
        super("amoswap.d t1, t2, (t3)",
                "Atomic swap : Set t1 to the double word at the address in t3, and store t2 there. The exchange is indivisible, which makes this the natural way to implement a spinlock",
                "00001", "011", true);
    }

    public long compute(long loaded, long value) {
        return value;
    }
}
