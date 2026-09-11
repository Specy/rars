package app.specy.rars.riscv.instructions;

public class AMOMAXD extends Atomic {
    public AMOMAXD() {
        super("amomax.d t1, t2, (t3)",
                "Atomic maximum (signed) : Set t1 to the double word at the address in t3, and store the larger of that value and t2 there, comparing as signed",
                "10100", "011", true);
    }

    public long compute(long loaded, long value) {
        return Math.max(loaded, value);
    }
}
