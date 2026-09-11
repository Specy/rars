package app.specy.rars.riscv.instructions;

public class AMOANDW extends Atomic {
    public AMOANDW() {
        super("amoand.w t1, t2, (t3)",
                "Atomic and : Set t1 to the word at the address in t3, and store the bitwise AND of that value and t2 there. Clears a bit mask in shared memory",
                "01100", "010");
    }

    public long compute(long loaded, long value) {
        return loaded & value;
    }
}
