package app.specy.rars.riscv.instructions;

public class ORCB extends BitManipulation {
    public ORCB() {
        super("orc.b t1,t2",
                "Bitwise OR combine within bytes : Set every byte of t1 to 0xFF when the matching byte of t2 has any bit set, and to 0 otherwise. Used to find a zero byte inside a word",
                "0010100", "00111", "101");
    }

    public long compute(long value) {
        return orcb(value);
    }

    private static long orcb(long value) {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 8) {
            if (((value >>> shift) & 0xFF) != 0) {
                result |= 0xFFL << shift;
            }
        }
        return result;
    }
}
