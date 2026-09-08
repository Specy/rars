package app.specy.rarsjs;

import app.specy.rars.ErrorList;
import app.specy.rars.ErrorMessage;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

import java.util.ArrayList;
import java.util.List;

public class JsCompilationResult {

    public class JsCompilationError {
        private boolean isWarning;
        private String message;
        private JsSourceLocation[] macroExpansionTrace;
        private String sourcePath;
        private int sourceLine;
        private int sourceColumn;

        public JsCompilationError(ErrorMessage error) {
            this.isWarning = error.isWarning();
            this.message = error.getMessage();
            this.macroExpansionTrace = error.getMacroExpansionTrace().stream()
                    .map(JsSourceLocation::new)
                    .toArray(JsSourceLocation[]::new);
            this.sourcePath = error.getSourcePath();
            this.sourceLine = error.getLine();
            this.sourceColumn = error.getPosition();
        }

        @JSExport
        @JSProperty("isWarning")
        public boolean isWarning() {
            return isWarning;
        }

        @JSExport
        @JSProperty
        public String getMessage() {
            return message;
        }

        @JSExport
        @JSProperty
        public JsSourceLocation[] getMacroExpansionTrace() {
            return macroExpansionTrace;
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
        public int getSourceColumn() {
            return sourceColumn;
        }
    }

    private String report;
    private List<JsCompilationError> errors;

    public JsCompilationResult(ErrorList errorReport) {
        this.report = errorReport.generateErrorAndWarningReport();
        this.errors = errorReport.getErrorMessages().stream()
                .map(JsCompilationError::new)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    @JSExport
    @JSProperty
    public String getReport() {
        return report;
    }

    @JSExport
    @JSProperty
    public boolean hasErrors() {
        return errors.stream().anyMatch(e -> !e.isWarning());
    }

    @JSExport
    @JSProperty
    public boolean hasWarnings() {
        return errors.stream().anyMatch(JsCompilationError::isWarning);
    }

    @JSExport
    @JSProperty
    public JsCompilationError[] getErrors() {
        return errors.toArray(new JsCompilationError[0]);
    }
}
