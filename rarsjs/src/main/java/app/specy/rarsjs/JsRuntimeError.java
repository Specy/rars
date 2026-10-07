package app.specy.rarsjs;

import app.specy.rars.ErrorMessage;
import app.specy.rars.SimulationException;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSExceptions;
import org.teavm.jso.JSObject;

/**
 * The error a run call rejects with when the program fails at runtime: a JS Error named
 * {@code RuntimeError}, typed so that a host never parses its text. It carries
 *
 * <ul>
 *     <li>{@code kind}: {@code exception} for a RISC-V exception the program had no handler for,
 *     {@code syscall} for a service that refused its arguments or input, {@code handler} for a host
 *     handler that failed, {@code internal} for the simulator failing;</li>
 *     <li>{@code address}: the instruction that failed, or the program counter that could not be
 *     fetched, as a signed 32 bit int like every address of this package;</li>
 *     <li>{@code sourcePath} and {@code line} (one-based): the failing statement's source, or null;</li>
 *     <li>{@code message}: RARS's own words, such as "Runtime exception at 0x00400004: invalid or
 *     unimplemented syscall service: 99";</li>
 *     <li>{@code cause}: for a handler failure, what the handler threw or rejected with.</li>
 * </ul>
 */
final class JsRuntimeError {
    private JsRuntimeError() {
    }

    static JSObject of(SimulationException failure) {
        ErrorMessage error = failure.error();
        String path = error.getSourcePath();
        Throwable cause = failure.getCause();
        JSObject reason = null;
        if (cause instanceof JsHandlerFailure) {
            reason = ((JsHandlerFailure) cause).reason;
        } else if (cause instanceof JsHandlerRejection) {
            reason = ((JsHandlerRejection) cause).reason;
        } else if (cause != null) {
            reason = JSExceptions.getJSException(cause);
        }
        return create(failure.getKind().label(), failure.getAddress(),
                path == null || path.isEmpty() ? null : path, error.getLine(),
                error.getMessage().trim(), reason);
    }

    @JSBody(params = { "kind", "address", "sourcePath", "line", "message", "cause" },
            script = "var error = new Error(message);"
                    + "error.name = 'RuntimeError';"
                    + "error.kind = kind;"
                    + "error.address = address;"
                    + "error.sourcePath = sourcePath === void 0 ? null : sourcePath;"
                    + "error.line = line > 0 ? line : null;"
                    + "if (cause !== null && cause !== void 0) error.cause = cause;"
                    + "return error;")
    private static native JSObject create(String kind, int address, String sourcePath, int line,
            String message, JSObject cause);
}
