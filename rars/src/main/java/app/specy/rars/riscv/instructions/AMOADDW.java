package app.specy.rars.riscv.instructions;

public class AMOADDW extends Atomic {
    public AMOADDW() {
        super("amoadd.w t1, t2, (t3)",
                "Atomic add : Set t1 to the word at the address in t3, and store that value plus t2 there. Useful for a shared counter that several agents increment",
                "00000", "010");
    }

    public long compute(long loaded, long value) {
        return loaded + value;
    }
}
