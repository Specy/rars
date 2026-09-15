package app.specy.rarsjs;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;

/**
 * One entry of the undo history: everything a single `undo()` reverts. That is either one executed
 * instruction, whose back steps are all the effects it had, or one poke, whose writes are the values
 * the host changed between two instructions.
 *
 * Like the back steps it carries, an entry is an ordinary JS object, so a host can clone or
 * serialize the whole history as it comes.
 */
public final class JsUndoGroup {

    private JsUndoGroup() {
    }

    static JSObject instruction(int pc, JSArray<JSObject> steps) {
        return create("instruction", pc, steps, JSArray.<JSObject>create(0));
    }

    static JSObject poke(int pc, JSArray<JSObject> steps, JSArray<JSObject> writes) {
        return create("poke", pc, steps, writes);
    }

    @JSBody(params = { "kind", "pc", "steps", "writes" },
            script = "return { kind: kind, pc: pc, steps: steps, writes: writes };")
    private static native JSObject create(String kind, int pc, JSObject steps, JSObject writes);
}
