package app.specy.rars.riscv.instructions;

public class AMOMAXUD extends Atomic {
    public AMOMAXUD() {
        super("amomaxu.d t1, t2, (t3)",
                "Atomic maximum (unsigned) : Set t1 to the double word at the address in t3, and store the larger of that value and t2 there, comparing as unsigned",
                "11100", "011", true);
    }

    public long compute(long loaded, long value) {
        return Long.compareUnsigned(loaded, value) >= 0 ? loaded : value;
    }
}
