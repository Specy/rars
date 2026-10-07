package app.specy.rarsjs;

import app.specy.rars.riscv.io.RISCVIOError;
import org.teavm.jso.JSObject;

/**
 * A host handler's promise rejected. The file services answer the program with -1 for it, as for
 * any failed file operation; every other service ends the run with a runtime error of kind
 * {@code handler} whose cause is the rejection's reason.
 */
final class JsHandlerRejection extends RISCVIOError {
    final JSObject reason;

    JsHandlerRejection(String message, JSObject reason) {
        super(message);
        this.reason = reason;
    }
}
