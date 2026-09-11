package app.specy.rars.riscv.instructions;

public class LRW extends LoadReserved {
    public LRW() {
        super("lr.w t1, (t2)",
                "Load reserved (word) : Set t1 to the word at the address in t2 and reserve that address. Pair with sc.w to build an atomic read-modify-write that can be retried",
                "010");
    }
}
