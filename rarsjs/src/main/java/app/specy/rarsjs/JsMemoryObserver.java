package app.specy.rarsjs;

import app.specy.rars.riscv.hardware.AccessNotice;
import app.specy.rars.riscv.hardware.MemoryAccessNotice;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSFunction;

import java.util.Observable;
import java.util.Observer;

/**
 * Bridges one RARS memory observation to plain JavaScript functions.
 *
 * The callbacks run synchronously inside the instruction that caused the access, so a returned
 * promise is ignored rather than awaited: a peripheral that had to suspend the program here would
 * stop the guest mid-load, which no memory-mapped device convention expects.
 */
class JsMemoryObserver implements Observer {

    final int handle;
    final int startAddress;
    final int endAddress;

    private final JSFunction onRead;
    private final JSFunction onWrite;
    /** A range observer reports the access width; a single-word observer's is always a word. */
    private final boolean reportsLength;

    private JsMemoryObserver(int handle, int startAddress, int endAddress, JSFunction onRead,
            JSFunction onWrite, boolean reportsLength) {
        this.handle = handle;
        this.startAddress = startAddress;
        this.endAddress = endAddress;
        // Callers omit the direction they do not care about, and an omitted argument arrives as
        // undefined, which a plain Java null check would let through.
        this.onRead = isNullish(onRead) ? null : onRead;
        this.onWrite = isNullish(onWrite) ? null : onWrite;
        this.reportsLength = reportsLength;
    }

    @JSBody(params = "value", script = "return value === null || value === void 0;")
    private static native boolean isNullish(JSObject value);

    /** Writes anywhere in an address range, the shape a framebuffer wants. */
    static JsMemoryObserver overRange(int handle, int startAddress, int endAddress, JSFunction onWrite) {
        return new JsMemoryObserver(handle, startAddress, endAddress, null, onWrite, true);
    }

    /** Reads and writes of one word, the shape a memory-mapped register wants. */
    static JsMemoryObserver atWord(int handle, int address, JSFunction onRead, JSFunction onWrite) {
        return new JsMemoryObserver(handle, address, address, onRead, onWrite, false);
    }

    @Override
    public void update(Observable observable, Object argument) {
        if (!(argument instanceof MemoryAccessNotice)) {
            return;
        }
        MemoryAccessNotice notice = (MemoryAccessNotice) argument;
        boolean isRead = notice.getAccessType() == AccessNotice.READ;
        JSFunction handler = isRead ? onRead : onWrite;
        if (handler == null) {
            return;
        }
        int address = notice.getAddress();
        int value = notice.getValue();
        // The call and its catch are JavaScript, so that what the observer threw arrives as it was
        // thrown: on the WebAssembly build a Java catch only sees a wrapper without it. Nothing is
        // allocated unless it threw, because this runs for every store into a watched range.
        JSObject failure = reportsLength
                ? observeRange(handler, address, notice.getLength(), value)
                : observeWord(handler, address, value);
        if (!isNullish(failure)) {
            // An observer is host code like an IO handler: a run it fails ends as the host's failure.
            JSObject reason = thrownBy(failure);
            throw new JsHandlerFailure("Memory observer threw: " + describe(reason), reason);
        }
    }

    @JSBody(params = {"handler", "address", "length", "value"},
            script = "try { handler.call(handler, address, length, value); } catch (e) { return { error: e }; } return null;")
    private static native JSObject observeRange(JSFunction handler, int address, int length, int value);

    @JSBody(params = {"handler", "address", "value"},
            script = "try { handler.call(handler, address, value); } catch (e) { return { error: e }; } return null;")
    private static native JSObject observeWord(JSFunction handler, int address, int value);

    @JSBody(params = "failure", script = "return failure.error;")
    private static native JSObject thrownBy(JSObject failure);

    @JSBody(params = "error", script = "return error instanceof Error ? (error.message || String(error)) : String(error);")
    private static native String describe(JSObject error);
}
