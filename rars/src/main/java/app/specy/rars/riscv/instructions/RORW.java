package app.specy.rars.riscv.instructions;

public class RORW extends ArithmeticW {
    public RORW() {
        super("rorw t1,t2,t3",
                "Rotate right (32 bit) : Set t1 to the low 32 bits of t2 rotated right by the low 5 bits of t3, sign extended",
                "0110000", "101", new ROR());
    }
}
