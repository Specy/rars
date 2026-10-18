package app.specy.rarsjs;

import app.specy.rars.assembler.TokenList;
import app.specy.rars.assembler.TokenTypes;
import app.specy.rars.riscv.ExtendedInstruction;
import app.specy.rars.riscv.Instruction;
import app.specy.rars.riscv.InstructionSet;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JsInstruction {

    String name;
    String example;
    String description;
    JsRiscVToken[] tokens;
    boolean isRv64Only;
    boolean isPseudo;
    String[] expansion;

    /*
     * The forms of the 32 bit instruction set, keyed by `formKey`, built on first use.
     */
    private static Set<String> rv32Forms;

    public JsInstruction(Instruction ins) {
        this.name = ins.getName();
        this.example = ins.getExampleFormat();
        this.description = ins.getDescription();
        this.tokens = new JsRiscVToken[ins.getTokenList().size()];
        for (int i = 0; i < ins.getTokenList().size(); i++) {
            this.tokens[i] = new JsRiscVToken(ins.getTokenList().get(i));
        }
        // RV64 only means rv32 does not accept the form. RARS also flags the rv64 width of forms
        // rv32 has, such as `slli`, `srli` and `srai` with a 6 bit shift amount and the rv64
        // expansions of `li`, so those are cleared here; the simulator's own flag stays as it is.
        this.isRv64Only = ins.isRv64Only() && !rv32Forms().contains(formKey(ins));
        this.isPseudo = ins instanceof ExtendedInstruction;
        this.expansion = ins instanceof ExtendedInstruction
                ? templates(((ExtendedInstruction) ins).getBasicIntructionTemplateList())
                : new String[0];
    }

    /*
     * The templates as PseudoOps writes them, without the spaces that line its columns up.
     */
    private static String[] templates(List<String> list) {
        if (list == null) {
            return new String[0];
        }
        String[] templates = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            templates[i] = list.get(i).trim();
        }
        return templates;
    }

    /*
     * The mnemonic and the operand token types, with every integer width read as one, so that an
     * rv64 form matches the rv32 form it widens.
     */
    private static String formKey(Instruction ins) {
        StringBuilder key = new StringBuilder(ins.getName());
        TokenList tokens = ins.getTokenList();
        for (int i = 1; i < tokens.size(); i++) {
            TokenTypes type = tokens.get(i).getType();
            key.append(' ').append(TokenTypes.isIntegerTokenType(type) ? "INTEGER" : type.toString());
        }
        return key.toString();
    }

    /*
     * Builds a separate 32 bit set rather than switching the global one, and restores the width
     * flag its instructions read while they are built.
     */
    private static Set<String> rv32Forms() {
        if (rv32Forms == null) {
            boolean rv64 = InstructionSet.rv64;
            InstructionSet.rv64 = false;
            try {
                InstructionSet set = new InstructionSet();
                set.populate();
                Set<String> forms = new HashSet<>();
                for (Instruction ins : set.getInstructionList()) {
                    forms.add(formKey(ins));
                }
                rv32Forms = forms;
            } finally {
                InstructionSet.rv64 = rv64;
            }
        }
        return rv32Forms;
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

    @JSExport
    public boolean getIsPseudo() {
        return isPseudo;
    }

    /**
     * The basic instructions the assembler expands this pseudo-instruction into, in order, as
     * templates with RARS's placeholders left in: `la t1,label` gives `auipc RG1, PCH2` and
     * `addi RG1, RG1, PCL2`. Empty for a basic instruction.
     */
    @JSExport
    public String[] getExpansion() {
        return expansion;
    }


}
