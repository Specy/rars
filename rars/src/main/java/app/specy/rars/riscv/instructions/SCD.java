package app.specy.rars.riscv.instructions;

public class SCD extends StoreConditional {
    public SCD() {
        super("sc.d t1, t2, (t3)",
                "Store conditional (double word) : Store t2 as a double word at the address in t3 only if that address is still reserved by an lr.d, then set t1 to 0 on success or 1 on failure. Always branch on t1 and retry from the lr.d when it fails",
                "011", true);
    }
}
