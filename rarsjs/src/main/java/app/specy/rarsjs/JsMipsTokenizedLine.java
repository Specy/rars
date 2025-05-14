package app.specy.marsjs;

import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

public class JsMipsTokenizedLine {
    String line;
    JsMipsToken[] tokens;

    public JsMipsTokenizedLine(String line, JsMipsToken[] tokens) {
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
    public JsMipsToken[] getTokens() {
        return tokens;
    }
}
