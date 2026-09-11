package app.specy.rars.riscv.instructions;

public class AMOMINUW extends Atomic {
    public AMOMINUW() {
        super("amominu.w t1, t2, (t3)",
                "Atomic minimum (unsigned) : Set t1 to the word at the address in t3, and store the smaller of that value and t2 there, comparing as unsigned",
                "11000", "010");
    }

    public long compute(long loaded, long value) {
        return Long.compareUnsigned(loaded, value) <= 0 ? loaded : value;
    }

    protected int computeW(int loaded, int value) {
        return Integer.compareUnsigned(loaded, value) <= 0 ? loaded : value;
    }
}
