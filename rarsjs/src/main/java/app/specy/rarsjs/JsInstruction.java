package app.specy.marsjs;

import app.specy.mars.assembler.Token;
import app.specy.mars.mips.instructions.Instruction;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

public class JsInstruction {

    String name;
    String example;
    String description;
    JsMipsToken[] tokens;

    public JsInstruction(Instruction ins) {
        this.name = ins.getName();
        this.example = ins.getExampleFormat();
        this.description = ins.getDescription();
        this.tokens = new JsMipsToken[ins.getTokenList().size()];
        for(int i = 0; i < ins.getTokenList().size(); i++) {
            this.tokens[i] = new JsMipsToken(ins.getTokenList().get(i));
        }
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
    public JsMipsToken[] getTokens() {
        return tokens;
    }

}
