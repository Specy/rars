package app.specy.rarsjs;

import app.specy.rars.riscv.io.RISCVIOFailure;
import org.teavm.jso.JSObject;

/**
 * A host handler threw or broke its contract. The run ends with it, as a runtime error of kind
 * {@code handler} whose cause is what the handler threw, when it threw something.
 */
final class JsHandlerFailure extends RISCVIOFailure {
    /** What the handler threw, or null when it broke its contract instead. */
    final JSObject reason;

    JsHandlerFailure(String message, JSObject reason) {
        super(message);
        this.reason = reason;
    }
}
