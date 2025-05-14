package app.specy.marsjs;

import app.specy.mars.simulator.BackStepper;
import org.teavm.jso.JSClass;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;


@JSClass
public class JsBackStep {

    int action;
    int pc;
    int param1;
    int param2;

    public JsBackStep(BackStepper.BackStep backStep) {
        this.action = backStep.getAction();
        this.pc = backStep.getPc();
        this.param1 = backStep.getParam1();
        this.param2 = backStep.getParam2();
    }


    @JSExport
    @JSProperty
    public int getAction() {
        return action;
    }

    @JSExport
    @JSProperty
    public int getPc() {
        return pc;
    }

    @JSExport
    @JSProperty
    public int getParam1() {
        return param1;
    }

    @JSExport
    @JSProperty
    public int getParam2() {
        return param2;
    }

}
