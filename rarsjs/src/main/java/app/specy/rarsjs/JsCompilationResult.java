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
        private String macroExpansionHistory;
        private String filename;
        private int lineNumber;
        private int columnNumber;

        public JsCompilationError(ErrorMessage error) {
            this.isWarning = error.isWarning();
            this.message = error.getMessage();
            this.macroExpansionHistory = error.getMacroExpansionHistory();
            this.filename = error.getFilename();
            this.lineNumber = error.getLine();
            this.columnNumber = error.getPosition();
        }

        @JSExport
        @JSProperty
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
        public String getMacroExpansionHistory() {
            return macroExpansionHistory;
        }

        @JSExport
        @JSProperty
        public String getFilename() {
            return filename;
        }

        @JSExport
        @JSProperty
        public int getLineNumber() {
            return lineNumber;
        }

        @JSExport
        @JSProperty
        public int getColumnNumber() {
            return columnNumber;
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
        return !errors.isEmpty();
    }


    @JSExport
    @JSProperty
    public JsCompilationError[] getErrors() {
        return errors.toArray(new JsCompilationError[0]);
    }
}
