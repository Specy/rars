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
                Long.toString(backStep.getOldValue()), Long.toString(backStep.getNewValue()),
                backStep.isPoke(), Long.toString(backStep.getSerial()));
    }

    /*
     * `isPoke` is the discriminator this stack needs: a poke entry keeps pc == -1, which a host
     * write made before anything ran also has, so the pc alone cannot tell the two apart.
     *
     * `oldValue` and `newValue` are the two sides of the write, whole: every value here is a 64 bit
     * long - a register of an RV64 target, or the value of an `sd` - so they cross as signed
     * decimal strings, the shape `JsPokeWrite` and `getRegistersValuesLong` already use, and the
     * shape @specy/mips reports its back steps in, so that one reading serves both. `param1`
     * crosses unsigned, so an address at or above 0x80000000 (the memory-mapped devices) reads as
     * the address it is; a register or CSR number or an index is never negative and reads as
     * before.
     */
    @JSBody(params = { "action", "pc", "param1", "oldValue", "newValue", "isPoke", "serial" },
            script = "return { action: action, pc: pc, param1: param1 >>> 0, oldValue: oldValue,"
                    + " newValue: newValue, isPoke: isPoke, serial: serial };")
    private static native JSObject create(int action, int pc, int param1, String oldValue,
            String newValue, boolean isPoke, String serial);
}
