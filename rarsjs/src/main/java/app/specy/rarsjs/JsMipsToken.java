package app.specy.marsjs;

import app.specy.mars.assembler.Token;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

public class JsMipsToken {
    int sourceLine;
    int sourceColumn;
    int originalSourceLine;
    String value;
    String type;

    public JsMipsToken(Token token) {
        this.sourceLine = token.getSourceLine();
        this.sourceColumn = token.getStartPos();
        this.originalSourceLine = token.getOriginalSourceLine();
        this.value = token.getValue();
        this.type = token.getType().toString();
    }

    @JSExport
    @JSProperty
    public int getSourceLine() {
        return sourceLine;
    }

    @JSExport
    @JSProperty
    public int getSourceColumn() {
        return sourceColumn;
    }

    @JSExport
    @JSProperty
    public int getOriginalSourceLine() {
        return originalSourceLine;
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