package app.specy.rars.riscv.instructions;

public class AMOMINW extends Atomic {
    public AMOMINW() {
        super("amomin.w t1, t2, (t3)",
                "Atomic minimum (signed) : Set t1 to the word at the address in t3, and store the smaller of that value and t2 there, comparing as signed",
                "10000", "010");
    }

    public long compute(long loaded, long value) {
        return Math.min(loaded, value);
    }
}
