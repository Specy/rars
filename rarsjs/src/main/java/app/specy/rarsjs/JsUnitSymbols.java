package app.specy.rarsjs;

import app.specy.rars.ErrorList;
import app.specy.rars.ErrorMessage;
import app.specy.rars.assembler.GnuAssembler;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

/** The globals one GNU compiler unit defines and uses, or why it could not be parsed. */
public class JsUnitSymbols {
    private final String[] defined, weak, references, errors;

    JsUnitSymbols(GnuAssembler.UnitSymbols symbols, ErrorList errorList) {
        String[] none = new String[0];
        defined = symbols == null ? none : symbols.defined;
        weak = symbols == null ? none : symbols.weak;
        references = symbols == null ? none : symbols.references;
        if (errorList == null) {
            errors = none;
        } else {
            errors = errorList.getErrorMessages().stream()
                    .filter(message -> !message.isWarning())
                    .map(JsUnitSymbols::describe)
                    .toArray(String[]::new);
        }
    }

    private static String describe(ErrorMessage message) {
        return message.getSourcePath() + ":" + message.getLine() + ": " + message.getMessage();
    }

    /** Globals the unit defines, strong or weak. */
    @JSExport
    @JSProperty
    public String[] getDefined() {
        return defined;
    }

    /** The defined globals that are weak. */
    @JSExport
    @JSProperty
    public String[] getWeak() {
        return weak;
    }

    /** Globals the unit uses without defining, other than weak references. */
    @JSExport
    @JSProperty
    public String[] getReferences() {
        return references;
    }

    /** Why the unit could not be parsed; empty when it parsed. */
    @JSExport
    @JSProperty
    public String[] getErrors() {
        return errors;
    }
}
