package app.specy.rars;

import app.specy.rars.assembler.SourceLine;
import app.specy.rars.assembler.SourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/*
Copyright (c) 2003-2012, Pete Sanderson and Kenneth Vollmar

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT
OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

/** Represents an error or warning detected during tokenizing, assembly, or simulation. */
public class ErrorMessage {
    private boolean isWarning;
    private String sourcePath;
    private int sourceLine;
    private int sourceColumn;
    private String message;
    private List<SourceLocation> macroExpansionTrace;

    public static final boolean WARNING = true;
    public static final boolean ERROR = false;

    public ErrorMessage(RISCVprogram sourceProgram, int line, int position, String message) {
        this(ERROR, sourceProgram, line, position, message);
    }

    public ErrorMessage(boolean isWarning, RISCVprogram sourceProgram, int line, int position,
                        String message) {
        this.isWarning = isWarning;
        if (sourceProgram == null) {
            this.sourcePath = "";
            this.sourceLine = line;
        } else if (sourceProgram.getSourceLineList() == null || line < 1
                || line > sourceProgram.getSourceLineList().size()) {
            this.sourcePath = sourceProgram.getFilename();
            this.sourceLine = line;
        } else {
            SourceLine originalLine = sourceProgram.getSourceLineList().get(line - 1);
            this.sourcePath = originalLine.getSourcePath();
            this.sourceLine = originalLine.getLineNumber();
        }
        this.sourceColumn = normalizeColumn(this.sourceLine, position);
        this.message = message;
        this.macroExpansionTrace = getExpansionTrace(sourceProgram);
    }

    public ErrorMessage(ProgramStatement statement, String message) {
        this(statement, 0, message);
    }

    /** Creates a diagnostic at the original source location of a statement. */
    public ErrorMessage(ProgramStatement statement, int position, String message) {
        this.isWarning = ERROR;
        this.sourcePath = statement.getSourcePath();
        this.sourceLine = statement.getSourceLine();
        this.sourceColumn = normalizeColumn(this.sourceLine, position);
        this.message = message;
        this.macroExpansionTrace = statement.getMacroExpansionTrace();
    }

    public String getFilename() {
        return sourcePath;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public int getLine() {
        return sourceLine;
    }

    public int getPosition() {
        return sourceColumn;
    }

    public String getMessage() {
        return message;
    }

    public boolean isWarning() {
        return isWarning;
    }

    public List<SourceLocation> getMacroExpansionTrace() {
        return new ArrayList<>(macroExpansionTrace);
    }

    public String getMacroExpansionHistory() {
        return macroExpansionTrace.stream()
                .map(SourceLocation::toString)
                .collect(Collectors.joining(" -> "));
    }

    private static List<SourceLocation> getExpansionTrace(RISCVprogram sourceProgram) {
        if (sourceProgram == null || sourceProgram.getLocalMacroPool() == null) {
            return new ArrayList<>();
        }
        return sourceProgram.getLocalMacroPool().getExpansionTrace();
    }

    private static int normalizeColumn(int sourceLine, int sourceColumn) {
        return sourceLine > 0 && sourceColumn < 1 ? 1 : sourceColumn;
    }

    public String generateReport() {
        String out = (isWarning ? ErrorList.WARNING_MESSAGE_PREFIX : ErrorList.ERROR_MESSAGE_PREFIX)
                + ErrorList.FILENAME_PREFIX;
        if (!sourcePath.isEmpty()) {
            out += sourcePath;
        }
        if (sourceLine > 0) {
            out += ErrorList.LINE_PREFIX + sourceLine;
        }
        if (sourceColumn > 0) {
            out += ErrorList.POSITION_PREFIX + sourceColumn;
        }
        if (!macroExpansionTrace.isEmpty()) {
            out += " (macro expansion: " + getMacroExpansionHistory() + ")";
        }
        return out + ErrorList.MESSAGE_SEPARATOR + message + "\n";
    }

    @Override
    public String toString() {
        return generateReport();
    }
}
