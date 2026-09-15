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
                (int) backStep.getParam2(), backStep.isPoke());
    }

    /*
     * `isPoke` is the discriminator this stack needs: a poke entry keeps pc == -1, which a host
     * write made before anything ran also has, so the pc alone cannot tell the two apart.
     */
    @JSBody(params = { "action", "pc", "param1", "param2", "isPoke" },
            script = "return { action: action, pc: pc, param1: param1, param2: param2, isPoke: isPoke };")
    private static native JSObject create(int action, int pc, int param1, int param2, boolean isPoke);
}
