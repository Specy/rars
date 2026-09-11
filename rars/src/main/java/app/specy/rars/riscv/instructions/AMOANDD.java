package app.specy.rars.riscv.instructions;

public class AMOANDD extends Atomic {
    public AMOANDD() {
        super("amoand.d t1, t2, (t3)",
                "Atomic and : Set t1 to the double word at the address in t3, and store the bitwise AND of that value and t2 there. Clears a bit mask in shared memory",
                "01100", "011", true);
    }

    public long compute(long loaded, long value) {
        return loaded & value;
    }
}
