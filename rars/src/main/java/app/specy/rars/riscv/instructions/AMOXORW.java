package app.specy.rars.riscv.instructions;

public class AMOXORW extends Atomic {
    public AMOXORW() {
        super("amoxor.w t1, t2, (t3)",
                "Atomic xor : Set t1 to the word at the address in t3, and store the bitwise XOR of that value and t2 there",
                "00100", "010");
    }

    public long compute(long loaded, long value) {
        return loaded ^ value;
    }
}
