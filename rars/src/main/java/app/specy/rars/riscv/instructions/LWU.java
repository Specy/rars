package app.specy.rars.riscv.instructions;

import app.specy.rars.Globals;
import app.specy.rars.riscv.hardware.AddressErrorException;

public class LWU extends Load {
    public LWU() {
        super("lwu t1, -100(t2)", "Set t1 to contents of effective memory word address without sign-extension", "110",true);
    }

    public long load(int address) throws AddressErrorException {
        return Globals.memory.getWord(address) & 0xFFFF_FFFFL;
    }
}
