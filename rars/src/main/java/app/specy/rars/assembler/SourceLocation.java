package app.specy.rars.assembler;

/** An original location in the virtual source tree. */
public final class SourceLocation {
    private final String sourcePath;
    private final int sourceLine;

    public SourceLocation(String sourcePath, int sourceLine) {
        this.sourcePath = sourcePath == null ? "" : sourcePath;
        this.sourceLine = sourceLine;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public int getSourceLine() {
        return sourceLine;
    }

    @Override
    public String toString() {
        return sourcePath + ":" + sourceLine;
    }
}
