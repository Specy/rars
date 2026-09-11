package app.specy.rars.riscv.instructions;

public class AMOADDD extends Atomic {
    public AMOADDD() {
        super("amoadd.d t1, t2, (t3)",
                "Atomic add : Set t1 to the double word at the address in t3, and store that value plus t2 there. Useful for a shared counter that several agents increment",
                "00000", "011", true);
    }

    public long compute(long loaded, long value) {
        return loaded + value;
    }
}
