package app.specy.marsjs.Main;

import app.specy.mars.Globals;
import app.specy.mars.MIPS;
import app.specy.mars.mips.hardware.RegisterFile;
import app.specy.mars.mips.instructions.SyscallLoader;
import app.specy.marsjs.JsMIPSIO;

public class Main {
    public static void main(String[] args) {
        try {
            MIPS.initializeMIPS();
            MIPS mips = MIPS.fromSource("""
                li $v0, 5
                move $t0, $v0
                move $t0, $v0
                move $t0, $v0
            
                li $t1, 20
                add $a0, $t0, $t1
                    """);
            mips.assemble();
            mips.initialize(true);
            mips.simulate(1000);
            System.out.println(RegisterFile.getUserRegister("$a0").getValue());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
