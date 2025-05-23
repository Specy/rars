package app.specy.rarsjs;

import app.specy.rars.riscv.Instruction;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

public class JsInstruction {

    String name;
    String example;
    String description;
    JsRiscVToken[] tokens;
    boolean isRv64Only;

    public JsInstruction(Instruction ins) {
        this.name = ins.getName();
        this.example = ins.getExampleFormat();
        this.description = ins.getDescription();
        this.tokens = new JsRiscVToken[ins.getTokenList().size()];
        for (int i = 0; i < ins.getTokenList().size(); i++) {
            this.tokens[i] = new JsRiscVToken(ins.getTokenList().get(i));
        }
        this.isRv64Only = ins.isRv64Only();
    }


    @JSExport
    @JSProperty
    public String getName() {
        return name;
    }

    @JSExport
    @JSProperty
    public String getExample() {
        return example;
    }

    @JSExport
    @JSProperty
    public String getDescription() {
        return description;
    }

    @JSExport
    @JSProperty
    public JsRiscVToken[] getTokens() {
        return tokens;
    }

    @JSExport
    public boolean getIsRv64Only() {
        return isRv64Only;
    }


}
