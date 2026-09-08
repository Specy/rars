package app.specy.rarsjs;

import app.specy.rars.assembler.SourceLocation;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

public class JsSourceLocation {
    private final String sourcePath;
    private final int sourceLine;

    public JsSourceLocation(SourceLocation location) {
        this.sourcePath = location.getSourcePath();
        this.sourceLine = location.getSourceLine();
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
}
