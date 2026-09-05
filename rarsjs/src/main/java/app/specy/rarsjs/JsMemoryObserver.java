package app.specy.rarsjs;

import app.specy.rars.riscv.hardware.AccessNotice;
import app.specy.rars.riscv.hardware.MemoryAccessNotice;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSFunction;
import org.teavm.jso.core.JSNumber;

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
        JSNumber address = JSNumber.valueOf(notice.getAddress());
        JSNumber value = JSNumber.valueOf(notice.getValue());
        if (reportsLength) {
            handler.call(handler, address, JSNumber.valueOf(notice.getLength()), value);
        } else {
            handler.call(handler, address, value);
        }
    }
}
