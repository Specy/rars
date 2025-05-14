package app.specy.marsjs;

import app.specy.mars.mips.hardware.StackFrame;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

public class JsStackFrame {
    public int pc;
    public int toAddress;
    public int sp;
    public int fp;
    public int[] registers;

    public JsStackFrame(StackFrame frame){
        this.pc = frame.getPC();
        this.toAddress = frame.getToAddress();
        this.sp = frame.getSP();
        this.fp = frame.getFP();
        this.registers = frame.getRegisters();
    }

    @JSExport
    @JSProperty
    public int getPc() {
        return pc;
    }

    @JSExport
    @JSProperty
    public int getToAddress() {
        return toAddress;
    }

    @JSExport
    @JSProperty
    public int getSp() {
        return sp;
    }

    @JSExport
    @JSProperty
    public int getFp() {
        return fp;
    }

    @JSExport
    @JSProperty
    public int[] getRegisters() {
        return registers;
    }
}
