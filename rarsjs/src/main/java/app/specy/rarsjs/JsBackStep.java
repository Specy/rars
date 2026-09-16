package app.specy.rarsjs;

import app.specy.rars.simulator.BackStepper;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/**
 * One entry of the raw back step stack, as `getUndoStack()` reports it.
 *
 * It is handed over as an ordinary JS object rather than as a Java object with accessors, so that a
 * host can clone it, `JSON.stringify` it or compare it against a plain object literal; reading its
 * properties is unchanged.
 */
public final class JsBackStep {

    private JsBackStep() {
    }

    static JSObject of(BackStepper.BackStep backStep) {
        return create(backStep.getAction(), backStep.getPc(), backStep.getParam1(),
                (int) backStep.getParam2(), Long.toString(backStep.getOldValue()),
                Long.toString(backStep.getNewValue()), backStep.isPoke());
    }

    /*
     * `isPoke` is the discriminator this stack needs: a poke entry keeps pc == -1, which a host
     * write made before anything ran also has, so the pc alone cannot tell the two apart.
     *
     * `oldValue` and `newValue` are the two sides of the write, whole: every value here is a 64 bit
     * long - a register of an RV64 target, or the value of an `sd` - and `param2` truncates to an
     * int, so they cross as signed decimal strings, the shape `JsPokeWrite` and
     * `getRegistersValuesLong` already use. `param1` and `param2` are unchanged, down to
     * PC_RESTORE's param2 of 0.
     */
    @JSBody(params = { "action", "pc", "param1", "param2", "oldValue", "newValue", "isPoke" },
            script = "return { action: action, pc: pc, param1: param1, param2: param2,"
                    + " oldValue: oldValue, newValue: newValue, isPoke: isPoke };")
    private static native JSObject create(int action, int pc, int param1, int param2, String oldValue,
            String newValue, boolean isPoke);
}
