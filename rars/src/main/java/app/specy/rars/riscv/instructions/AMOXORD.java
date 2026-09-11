package app.specy.rars.riscv.instructions;

public class AMOXORD extends Atomic {
    public AMOXORD() {
        super("amoxor.d t1, t2, (t3)",
                "Atomic xor : Set t1 to the double word at the address in t3, and store the bitwise XOR of that value and t2 there",
                "00100", "011", true);
    }

    public long compute(long loaded, long value) {
        return loaded ^ value;
    }
}
