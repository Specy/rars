package app.specy.rars.riscv.instructions;

public class LRD extends LoadReserved {
    public LRD() {
        super("lr.d t1, (t2)",
                "Load reserved (double word) : Set t1 to the double word at the address in t2 and reserve that address. Pair with sc.d to build an atomic read-modify-write that can be retried",
                "011", true);
    }
}
