package app.specy.rars.riscv.hardware;


public class StackFrame {
    private final int pc;
    private final int toAddress;
    private final int sp;
    private final int fp;
    private final int[] registers;

    public StackFrame(
            int pc,
            int sp,
            int fp,
            int toAddress,
            int[] registers
    ) {
        this.pc = pc;
        this.sp = sp;
        this.fp = fp;
        this.toAddress = toAddress;
        this.registers = registers;
    }


    public static StackFrame fromGlobalState(int returnAddress){
        return new StackFrame(
                RegisterFile.getProgramCounter(),
                (int) RegisterFile.getStackPointerRegister().getValue(),
                (int) RegisterFile.getFramePointerRegister().getValue(),
                returnAddress,
                RegisterFile.getRegistersValues()
        );
    }

    public int getPC() {
        return pc;
    }

    public int getToAddress() {
        return toAddress;
    }

    public int getSP() {
        return sp;
    }

    public int getFP() {
        return fp;
    }

    public int[] getRegisters() {
        return registers;
    }

}