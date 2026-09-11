package app.specy.rars.riscv.instructions;

public class ADDUW extends Arithmetic {
    public ADDUW() {
        super("add.uw t1,t2,t3",
                "Add unsigned word : Set t1 to the low 32 bits of t2, zero extended, plus t3. Adds an unsigned 32 bit index to a 64 bit base without a separate zero extension",
                "0000100", "000", true);
    }

    public long compute(long value, long value2) {
        return (value & 0xFFFFFFFFL) + value2;
    }
}
