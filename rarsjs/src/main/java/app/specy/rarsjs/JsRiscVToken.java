package app.specy.rarsjs;

import app.specy.rars.assembler.Token;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

public class JsRiscVToken {
    int sourceColumn;
    String value;
    String type;

    public JsRiscVToken(Token token) {
        this.sourceColumn = token.getStartPos();
        this.value = token.getValue();
        this.type = token.getType().toString();
    }

    @JSExport
    @JSProperty
    public int getSourceColumn() {
        return sourceColumn;
    }

    @JSExport
    @JSProperty
    public String getValue() {
        return value;
    }

    @JSExport
    @JSProperty
    public String getType() {
        return type;
    }
}
