package app.specy.rarsjs;

import app.specy.rars.riscv.io.RISCVIO;
import app.specy.rars.riscv.io.RISCVIOError;
import app.specy.rars.util.JavaRandom;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSExceptions;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.*;

import java.util.HashMap;
import java.util.Map;

public class JsRISCVIO extends RISCVIO {

    private final Map<String, JSFunction> handlers = new HashMap<>();

    @JSExport
    public JsRISCVIO() {
        super();
    }

    public void registerHandler(String name, JSFunction handler) {
        // An omitted handler arrives as undefined, which a Java null check would let through.
        if (isNullish(handler)) {
            handlers.remove(name);
        } else {
            handlers.put(name, handler);
        }
    }

    private JSObject callHandler(String name, JSObject... args) {
        return awaitIfThenable(name, invokeHandler(name, args));
    }

    /**
     * Calls a handler. Whatever it throws ends the run as the host's failure, with what it threw as
     * the cause.
     */
    private JSObject invokeHandler(String name, JSObject... args) {
        JSFunction handler = handlers.get(name);
        if (handler == null) throw new JsHandlerFailure("No handler registered for " + name, null);
        try {
            return callWith(handler, args);
        } catch (Throwable thrown) {
            JSObject reason = JSExceptions.getJSException(thrown);
            throw new JsHandlerFailure("Handler " + name + " threw: " + describe(reason), reason);
        }
    }

    private static JSObject callWith(JSFunction handler, JSObject... args) {
        if(args.length == 0) return (JSObject) handler.call(handler);
        if(args.length == 1) return (JSObject) handler.call(handler, args[0]);
        if(args.length == 2) return (JSObject) handler.call(handler, args[0], args[1]);
        if(args.length == 3) return (JSObject) handler.call(handler, args[0], args[1], args[2]);
        if(args.length == 4) return (JSObject) handler.call(handler, args[0], args[1], args[2], args[3]);
        if(args.length == 5) return (JSObject) handler.call(handler, args[0], args[1], args[2], args[3], args[4]);
        if(args.length == 6) return (JSObject) handler.call(handler, args[0], args[1], args[2], args[3], args[4], args[5]);
        if(args.length == 7) return (JSObject) handler.call(handler, args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
        if(args.length == 8) return (JSObject) handler.call(handler, args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7]);
        throw new IllegalArgumentException("Too many arguments, max 8");
    }

    /**
     * A handler may return the value directly or a promise of it. Anything thenable is awaited,
     * which suspends the simulation coroutine until it settles; everything else is passed through
     * untouched, so synchronous handlers keep running without ever yielding to the event loop.
     */
    private static JSObject awaitIfThenable(String name, JSObject value) {
        if (value == null || !isThenable(value)) return value;
        Settled settled = reflect(value).await();
        if (settled.isOk()) return settled.getValue();
        throw rejected(name, settled.getError());
    }

    private static RISCVIOError rejected(String name, JSObject error) {
        return new JsHandlerRejection("Handler " + name + " rejected: " + describe(error), error);
    }

    @JSBody(params = "value", script = "return value !== null && value !== void 0 && typeof value.then === 'function';")
    private static native boolean isThenable(JSObject value);

    /**
     * JSPromise.await() reports every rejection as a bare RuntimeException, dropping the reason,
     * so the outcome is reflected into a plain object that can be inspected from Java instead.
     */
    @JSBody(params = "value", script = "return Promise.resolve(value).then("
            + "function (v) { return { ok: true, value: v }; }, "
            + "function (e) { return { ok: false, error: e }; });")
    private static native JSPromise<Settled> reflect(JSObject value);

    @JSBody(params = "error", script = "return error instanceof Error ? (error.message || String(error)) : String(error);")
    private static native String describe(JSObject error);

    private interface Settled extends JSObject {
        @JSProperty("ok")
        boolean isOk();

        @JSProperty("value")
        JSObject getValue();

        @JSProperty("error")
        JSObject getError();
    }

    private int callIntHandler(String name, JSObject... args) {
        Object result = callHandler(name, args);
        if (result instanceof JSNumber) {
            return ((JSNumber) result).intValue();
        }
        throw new JsHandlerFailure("Handler " + name + " did not return an integer", null);
    }

    private String callStringHandler(String name, JSObject... args) {
        Object result = callHandler(name, args);
        if (result instanceof JSString) {
            return ((JSString) result).stringValue();
        }
        throw new JsHandlerFailure("Handler " + name + " did not return a string", null);
    }

    private double callDoubleHandler(String name, JSObject... args) {
        Object result = callHandler(name, args);
        if (result instanceof JSNumber) {
            return ((JSNumber) result).doubleValue();
        }
        throw new JsHandlerFailure("Handler " + name + " did not return a double", null);
    }

    /**
     * Calls a handler that answers like a read, with a tuple of the byte count (0 at the end, -1
     * on failure) and the bytes read, and copies the bytes into {@code destination}.
     */
    private int callReadHandler(String name, byte[] destination, int length, JSObject... args) {
        JSObject result = callHandler(name, args);
        if (result instanceof JSArray) {
            JSArray<JSObject> tuple = (JSArray<JSObject>) result;
            if (tuple.getLength() == 2 && tuple.get(0) instanceof JSNumber && tuple.get(1) instanceof JSArray) {
                int count = ((JSNumber) tuple.get(0)).intValue();
                if (count < 0) return -1;
                // The handler answers with plain JavaScript numbers, which is what its published
                // type says.
                JSArray<JSNumber> bytes = (JSArray<JSNumber>) tuple.get(1);
                int copied = Math.min(Math.min(bytes.getLength(), length), destination.length);
                for (int i = 0; i < copied; i++) destination[i] = (byte) bytes.get(i).intValue();
                return Math.min(count, copied);
            }
        }
        throw new JsHandlerFailure("Handler " + name + " must return a tuple of the byte count and the bytes read", null);
    }

    /** A Java byte array as the plain array of numbers from 0 to 255 the handler types promise. */
    private static JSArray<JSNumber> toByteNumbers(byte[] bytes) {
        JSArray<JSNumber> numbers = JSArray.create(bytes.length);
        for (int i = 0; i < bytes.length; i++) numbers.set(i, JSNumber.valueOf(bytes[i] & 0xff));
        return numbers;
    }

    @JSBody(params = "value", script = "return value === null;")
    private static native boolean isNull(JSObject value);

    @JSBody(params = "value", script = "return value === null || value === void 0;")
    private static native boolean isNullish(JSObject value);


    @Override
    public int openFile(String filename, int flags, boolean append) throws RISCVIOError {
        return callIntHandler("openFile", JSString.valueOf(filename), JSNumber.valueOf(flags), JSBoolean.valueOf(append));
    }

    @Override
    public void closeFile(int fileDescriptor) throws RISCVIOError {
        callHandler("closeFile", JSNumber.valueOf(fileDescriptor));
    }

    @Override
    public int writeFile(int fileDescriptor, byte[] buffer) throws RISCVIOError {
        return callIntHandler("writeFile", JSNumber.valueOf(fileDescriptor), toByteNumbers(buffer));
    }

    @Override
    public int readFile(int fileDescriptor, byte[] destination, int length) throws RISCVIOError {
        return callReadHandler("readFile", destination, length, JSNumber.valueOf(fileDescriptor), JSNumber.valueOf(length));
    }

    @Override
    public int confirm(String message) {
        return callIntHandler("confirm", JSString.valueOf(message));
    }

    /** The handler answers null when the user cancels the dialog. */
    @Override
    public String inputDialog(String message) {
        JSObject result = callHandler("inputDialog", JSString.valueOf(message));
        if (isNull(result)) return null;
        if (result instanceof JSString) return ((JSString) result).stringValue();
        throw new JsHandlerFailure("Handler inputDialog did not return a string or null", null);
    }

    @Override
    public void outputDialog(String message, int type) {
        callHandler("outputDialog", JSString.valueOf(message), JSNumber.valueOf(type));
    }

    @Override
    public String readInt() {
        return callStringHandler("readInt");
    }

    @Override
    public String readFloat() {
        return callStringHandler("readFloat");
    }

    @Override
    public String readDouble() {
        return callStringHandler("readDouble");
    }

    @Override
    public String readString() {
        return callStringHandler("readString");
    }

    @Override
    public String readChar() {
        return callStringHandler("readChar");
    }

    @Override
    public void printString(String text) {
        callHandler("printString", JSString.valueOf(text));
    }

    @Override
    public void sleep(int milliseconds) {
        callHandler("sleep", JSNumber.valueOf(milliseconds));
    }

    @Override
    public double time() {
        return callDoubleHandler("time");
    }

    @Override
    public int stdIn(byte[] buffer, int length) {
        return callReadHandler("stdIn", buffer, length, JSNumber.valueOf(length));
    }

    @Override
    public int seekFile(int fileDescriptor, int offset, int whence) throws RISCVIOError {
        return callIntHandler("seekFile", JSNumber.valueOf(fileDescriptor), JSNumber.valueOf(offset), JSNumber.valueOf(whence));
    }

    @Override
    public void stdOut(byte[] buffer) {
        callHandler("stdOut", toByteNumbers(buffer));
    }

    @Override
    public void stdErr(byte[] buffer) {
        callHandler("stdErr", toByteNumbers(buffer));
    }

    /**
     * The seed random generator {@code index} starts from: the randomSeed handler's answer, or host
     * randomness, as RARS has, when no handler is registered.
     */
    @Override
    public double randomSeed(int index) {
        if (!handlers.containsKey("randomSeed")) {
            return hostRandomSeed();
        }
        JSObject result = callHandler("randomSeed", JSNumber.valueOf(index));
        if (result instanceof JSNumber) {
            double seed = ((JSNumber) result).doubleValue();
            if (seed >= 0 && seed < JavaRandom.SEED_LIMIT && seed == Math.floor(seed)) {
                return seed;
            }
        }
        throw new JsHandlerFailure("Handler randomSeed did not return a whole number from 0 to 2^48 - 1", null);
    }
}
