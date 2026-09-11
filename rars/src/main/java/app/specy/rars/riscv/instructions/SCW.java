package app.specy.rars.riscv.instructions;

public class SCW extends StoreConditional {
    public SCW() {
        super("sc.w t1, t2, (t3)",
                "Store conditional (word) : Store t2 as a word at the address in t3 only if that address is still reserved by an lr.w, then set t1 to 0 on success or 1 on failure. Always branch on t1 and retry from the lr.w when it fails",
                "010");
    }
}
