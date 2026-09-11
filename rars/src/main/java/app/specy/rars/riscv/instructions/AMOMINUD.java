package app.specy.rars.riscv.instructions;

public class AMOMINUD extends Atomic {
    public AMOMINUD() {
        super("amominu.d t1, t2, (t3)",
                "Atomic minimum (unsigned) : Set t1 to the double word at the address in t3, and store the smaller of that value and t2 there, comparing as unsigned",
                "11000", "011", true);
    }

    public long compute(long loaded, long value) {
        return Long.compareUnsigned(loaded, value) <= 0 ? loaded : value;
    }
}
