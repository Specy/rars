package app.specy.rars;

import app.specy.rars.riscv.hardware.RegisterFile;

public class Main {

    public static void main(String[] args) {
        try {
            RARS.initializeRISCV();
            RARS.setIo(null);
            //TODO implement default IO
            RARS riscv = RARS.fromSource("""
                    li v0, 5
                    move t0, v0
                    move t0, v0
                    move t0, v0
                    
                    li t1, 20
                    add a0, t0, t1
                    """);
            riscv.assemble();
            riscv.initialize(true);
            System.out.println(riscv.hasTerminated());
            riscv.simulate(1000);
            System.out.println(riscv.hasTerminated());
            System.out.println(RegisterFile.getRegister("$a0").getValue());
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}
