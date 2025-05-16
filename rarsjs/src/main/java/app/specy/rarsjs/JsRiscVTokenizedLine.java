package app.specy.rarsjs;

import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

public class JsRiscVTokenizedLine {
    String line;
    JsRiscVToken[] tokens;

    public JsRiscVTokenizedLine(String line, JsRiscVToken[] tokens) {
        this.line = line;
        this.tokens = tokens;
    }

    @JSExport
    @JSProperty
    public String getLine() {
        return line;
    }

    @JSExport
    @JSProperty
    public JsRiscVToken[] getTokens() {
        return tokens;
    }
}
