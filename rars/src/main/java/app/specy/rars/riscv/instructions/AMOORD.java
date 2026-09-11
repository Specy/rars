package app.specy.rars.riscv.instructions;

public class AMOORD extends Atomic {
    public AMOORD() {
        super("amoor.d t1, t2, (t3)",
                "Atomic or : Set t1 to the double word at the address in t3, and store the bitwise OR of that value and t2 there. Sets a bit mask in shared memory",
                "01000", "011", true);
    }

    public long compute(long loaded, long value) {
        return loaded | value;
    }
}
