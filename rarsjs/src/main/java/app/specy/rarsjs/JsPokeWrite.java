package app.specy.rarsjs;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSNumber;

/**
 * One value a poke changed, with what the simulator held before the write and what it holds at the
 * end of the transaction. A register write names the register the way the package's getters spell
 * it; a memory write names its first address and carries a byte per address.
 *
 * Every register of this target is 64 bits wide, which no JS number holds, so a register value
 * crosses as a signed decimal string - the shape `getRegisterValueLong` and `getRegistersValuesLong`
 * already use - rather than as a number that would round or as a high/low pair that would not fit
 * one field. `BigInt(value)` reads it back exactly.
 *
 * Each write is an ordinary JS object with own enumerable properties, so that a host can clone,
 * serialize or deep-compare a writes list without mapping it first.
 */
public final class JsPokeWrite {

    private JsPokeWrite() {
    }

    static JSObject register(String name, long oldValue, long newValue) {
        return createRegister(name, Long.toString(oldValue), Long.toString(newValue));
    }

    static JSObject memory(int address, int[] oldBytes, int[] newBytes) {
        // Unsigned, so that a write into the memory mapped range reads as 0xffff0000 rather than
        // as the negative int the simulator holds it as.
        return createMemory(address & 0xffffffffL, toNumberArray(oldBytes), toNumberArray(newBytes));
    }

    private static JSObject toNumberArray(int[] values) {
        JSArray<JSNumber> array = JSArray.create(values.length);
        for (int i = 0; i < values.length; i++) {
            array.set(i, JSNumber.valueOf(values[i]));
        }
        return array;
    }

    @JSBody(params = { "name", "oldValue", "newValue" },
            script = "return { type: 'register', name: name, old: oldValue, new: newValue };")
    private static native JSObject createRegister(String name, String oldValue, String newValue);

    @JSBody(params = { "address", "oldBytes", "newBytes" },
            script = "return { type: 'memory', address: address, old: oldBytes, new: newBytes };")
    private static native JSObject createMemory(double address, JSObject oldBytes, JSObject newBytes);
}
