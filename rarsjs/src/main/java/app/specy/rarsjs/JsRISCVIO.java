package app.specy.rarsjs;

import app.specy.rars.riscv.io.RISCVIO;
import app.specy.rars.riscv.io.RISCVIOError;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
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
        handlers.put(name, handler);
    }

    private JSObject callHandler(String name, JSObject... args) {
        return awaitIfThenable(name, invokeHandler(name, args));
    }

    private JSObject invokeHandler(String name, JSObject... args) {
        JSFunction handler = handlers.get(name);
        if (handler == null) throw new IllegalArgumentException("No handler registered for " + name);
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
        return new RISCVIOError("Handler " + name + " rejected: " + describe(error));
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
        throw new IllegalArgumentException("Handler " + name + " did not return an integer");
    }

    private String callStringHandler(String name, JSObject... args) {
        Object result = callHandler(name, args);
        if (result instanceof JSString) {
            return ((JSString) result).stringValue();
        }
        throw new IllegalArgumentException("Handler " + name + " did not return a string");
    }

    private char callCharHandler(String name, JSObject... args) {
        String result = callStringHandler(name, args);
        if (result.length() == 1) {
            return result.charAt(0);
        }
        throw new IllegalArgumentException("Handler " + name + " did not return a char");
    }

    private float callFloatHandler(String name, JSObject... args) {
        Object result = callHandler(name, args);
        if (result instanceof JSNumber) {
            return ((JSNumber) result).floatValue();
        }
        throw new IllegalArgumentException("Handler " + name + " did not return a float");
    }

    private double callDoubleHandler(String name, JSObject... args) {
        Object result = callHandler(name, args);
        if (result instanceof JSNumber) {
            return ((JSNumber) result).doubleValue();
        }
        throw new IllegalArgumentException("Handler " + name + " did not return a double");
    }


    @Override
    public int openFile(String filename, int flags, boolean append) throws RISCVIOError {
        return callIntHandler("openFile", JSString.valueOf(filename), JSNumber.valueOf(flags), JSBoolean.valueOf(append));
    }

    @Override
    public void closeFile(int fileDescriptor) throws RISCVIOError {
        callHandler("closeFile", JSNumber.valueOf(fileDescriptor));
    }

    @Override
    public void writeFile(int fileDescriptor, byte[] buffer) throws RISCVIOError {
        callHandler("writeFile", JSNumber.valueOf(fileDescriptor), JSArray.of(buffer));
    }

    @Override
    public int readFile(int fileDescriptor, byte[] destination, int length) throws RISCVIOError {
        JSObject result = callHandler("readFile", JSNumber.valueOf(fileDescriptor), JSArray.of(destination), JSNumber.valueOf(length));
        if(result instanceof JSArray){
            JSArray<JSObject> array = (JSArray<JSObject>) result;
            if(array.getLength() != 2){
                throw new RISCVIOError("Read file expects a tuple of 2 elements, the first being if the EOF was reached (-1), and the second being the buffer");
            }
            JSNumber eof = (JSNumber) array.get(0);
            JSArray<Byte> buffer = (JSArray<Byte>) array.get(1);
            for(int i = 0; i < buffer.getLength(); i++){
                destination[i] = buffer.get(i);
            }
            return eof.intValue();
        }
        throw new RISCVIOError("Read file expects a tuple of 2 elements, the first being if the EOF was reached (-1), and the second being the buffer");
    }

    @Override
    public int confirm(String message) {
        return callIntHandler("confirm", JSString.valueOf(message));
    }

    @Override
    public String inputDialog(String message) {
        return callStringHandler("inputDialog", JSString.valueOf(message));
    }

    @Override
    public void outputDialog(String message, int type) {
        callHandler("outputDialog", JSString.valueOf(message), JSNumber.valueOf(type));
    }

    @Override
    public int askInt(String message) {
        return callIntHandler("askInt", JSString.valueOf(message));
    }


    @Override
    public double askDouble(String message) {
        return callDoubleHandler("askDouble", JSString.valueOf(message));
    }

    @Override
    public float askFloat(String message) {
        return callFloatHandler("askFloat", JSString.valueOf(message));
    }


    @Override
    public String askString(String message) {
        return callStringHandler("askString", JSString.valueOf(message));
    }

    @Override
    public int readInt() {
        return callIntHandler("readInt");
    }

    @Override
    public double readDouble() {
        return callDoubleHandler("readDouble");
    }

    @Override
    public float readFloat() {
        return callFloatHandler("readFloat");
    }


    @Override
    public String readString() {
        return callStringHandler("readString");
    }

    @Override
    public char readChar() {
        return callCharHandler("readChar");
    }

    @Override
    public void logLine(String message) {
        callHandler("logLine", JSString.valueOf(message));
    }

    @Override
    public void log(String message) {
        callHandler("log", JSString.valueOf(message));
    }

    @Override
    public void printChar(char c) {
        callHandler("printChar", JSString.valueOf(String.valueOf(c)));
    }

    @Override
    public void printDouble(double d) {
        callHandler("printDouble", JSNumber.valueOf(d));
    }

    @Override
    public void printFloat(float f) {
        callHandler("printFloat", JSNumber.valueOf(f));
    }

    @Override
    public void printInt(int i) {
        callHandler("printInt", JSNumber.valueOf(i));
    }

    @Override
    public void printString(String l) {
        callHandler("printString", JSString.valueOf(l));
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
    public void stdIn(byte[] buffer, int length) {
        callHandler("stdIn", JSArray.of(buffer), JSNumber.valueOf(length));
    }

    @Override
    public void stdOut(byte[] buffer) {
        callHandler("stdOut", JSArray.of(buffer));
    }

    @Override
    public void stdErr(byte[] buffer) {
        callHandler("stdErr", JSArray.of(buffer));
    }
}
