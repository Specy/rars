package app.specy.rarsjs.Main;

import app.specy.rars.RARS;
import app.specy.rars.riscv.hardware.RegisterFile;

public class Main {

    public static void main(String[] args) {
        try {
            RARS.initializeRISCV();
            RARS.setIo(null);
            //TODO implement default IO
            RARS riscv = RARS.fromSource("""
                        li t0, 5          # Load immediate value 5 into register t0
                        li t1, 7          # Load immediate value 7 into register t1
                        add t2, t0, t1    # Add t0 and t1, store result in t2
                    """);
            riscv.assemble();
            riscv.initialize(true);
            //System.out.println(riscv.hasTerminated());
            riscv.simulate(1000);
            //System.out.println(riscv.hasTerminated());
            System.out.println(RegisterFile.getRegister("t2").getValue());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
