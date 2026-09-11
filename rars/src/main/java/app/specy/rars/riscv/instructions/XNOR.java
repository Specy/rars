package app.specy.rars.riscv.instructions;

public class XNOR extends Arithmetic {
    public XNOR() {
        super("xnor t1,t2,t3",
                "Exclusive NOR : Set t1 to the bitwise NOT of the XOR of t2 and t3, so a bit is set wherever t2 and t3 agree",
                "0100000", "100");
    }

    public long compute(long value, long value2) {
        return ~(value ^ value2);
    }
}
