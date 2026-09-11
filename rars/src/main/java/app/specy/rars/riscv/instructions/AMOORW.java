package app.specy.rars.riscv.instructions;

public class AMOORW extends Atomic {
    public AMOORW() {
        super("amoor.w t1, t2, (t3)",
                "Atomic or : Set t1 to the word at the address in t3, and store the bitwise OR of that value and t2 there. Sets a bit mask in shared memory",
                "01000", "010");
    }

    public long compute(long loaded, long value) {
        return loaded | value;
    }
}
