package app.specy.rars.riscv.instructions;

public class ROLW extends ArithmeticW {
    public ROLW() {
        super("rolw t1,t2,t3",
                "Rotate left (32 bit) : Set t1 to the low 32 bits of t2 rotated left by the low 5 bits of t3, sign extended",
                "0110000", "001", new ROL());
    }
}
