package app.specy.rarsjs;

import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

public class JsRiscVTokenizedLine {
    String sourcePath;
    int sourceLine;
    String source;
    String processedSource;
    JsRiscVToken[] tokens;

    public JsRiscVTokenizedLine(String sourcePath, int sourceLine, String source,
                               String processedSource, JsRiscVToken[] tokens) {
        this.sourcePath = sourcePath;
        this.sourceLine = sourceLine;
        this.source = source;
        this.processedSource = processedSource;
        this.tokens = tokens;
    }

    @JSExport
    @JSProperty
    public String getSourcePath() {
        return sourcePath;
    }

    @JSExport
    @JSProperty
    public int getSourceLine() {
        return sourceLine;
    }

    @JSExport
    @JSProperty
    public String getSource() {
        return source;
    }

    @JSExport
    @JSProperty
    public String getProcessedSource() {
        return processedSource;
    }

    @JSExport
    @JSProperty
    public JsRiscVToken[] getTokens() {
        return tokens;
    }
}
