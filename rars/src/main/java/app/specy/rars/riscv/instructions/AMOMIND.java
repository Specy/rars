package app.specy.rars.riscv.instructions;

public class AMOMIND extends Atomic {
    public AMOMIND() {
        super("amomin.d t1, t2, (t3)",
                "Atomic minimum (signed) : Set t1 to the double word at the address in t3, and store the smaller of that value and t2 there, comparing as signed",
                "10000", "011", true);
    }

    public long compute(long loaded, long value) {
        return Math.min(loaded, value);
    }
}
