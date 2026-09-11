package app.specy.rars.riscv.instructions;

import app.specy.rars.riscv.InstructionSet;

public class REV8 extends BitManipulation {
    public REV8() {
        super("rev8 t1,t2",
                "Reverse bytes : Set t1 to t2 with its bytes in the opposite order, converting between little and big endian",
                InstructionSet.rv64 ? "0110101" : "0110100", "11000", "101");
    }

    public long compute(long value) {
        return Long.reverseBytes(value);
    }

    protected int computeW(int value) {
        return Integer.reverseBytes(value);
    }
}
