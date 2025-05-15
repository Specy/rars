package app.specy.rars.config;

import app.specy.rars.Globals;


public class SettingsProperties extends ConfigMap {
    public static final String ExtendedAssembler = "ExtendedAssembler";
    public static final String BareMachine = "BareMachine";
    public static final String AssembleOnOpen = "AssembleOnOpen";
    public static final String AssembleAll = "AssembleAll";
    public static final String LabelWindowVisibility = "LabelWindowVisibility";
    public static final String DisplayAddressesInHex = "DisplayAddressesInHex";
    public static final String DisplayValuesInHex = "DisplayValuesInHex";
    public static final String LoadExceptionHandler = "LoadExceptionHandler";
    public static final String DelayedBranching = "DelayedBranching";
    public static final String EditorLineNumbersDisplayed = "EditorLineNumbersDisplayed";
    public static final String WarningsAreErrors = "WarningsAreErrors";
    public static final String ProgramArguments = "ProgramArguments";
    public static final String DataSegmentHighlighting = "DataSegmentHighlighting";
    public static final String RegistersHighlighting = "RegistersHighlighting";
    public static final String StartAtMain = "StartAtMain";
    public static final String EditorCurrentLineHighlighting = "EditorCurrentLineHighlighting";
    public static final String PopupInstructionGuidance = "PopupInstructionGuidance";
    public static final String PopupSyscallInput = "PopupSyscallInput";
    public static final String GenericTextEditor = "GenericTextEditor";
    public static final String AutoIndent = "AutoIndent";
    public static final String SelfModifyingCode = "SelfModifyingCode";

    public static final String MemoryConfiguration = "MemoryConfiguration";

    public SettingsProperties() {
        super();
        reset();
    }

    public void reset() {
        put(AssembleAll, "false");
        put(AssembleOnOpen, "false");
        put(BareMachine, "false");
        put(DataSegmentHighlighting, "true");
        put(DelayedBranching, "false");
        put(DisplayAddressesInHex, "true");
        put(DisplayValuesInHex, "true");
        put(EditorCurrentLineHighlighting, "true");
        put(EditorLineNumbersDisplayed, "true");
        put(ExtendedAssembler, "true");
        put(LabelWindowVisibility, "false");
        put(LoadExceptionHandler, "false");
        put(ProgramArguments, "false");
        put(RegistersHighlighting, "true");
        put(StartAtMain, "false");
        put(WarningsAreErrors, "false");
        put(PopupInstructionGuidance, "true");

        put(MemoryConfiguration, "");
    }

    public boolean getDelayedBranchingEnabled() {
        return getBooleanValue(DelayedBranching);
    }

    public boolean getBareMachineEnabled() {
        return getBooleanValue(BareMachine);
    }

    public boolean getBackSteppingEnabled() {
        return (Globals.program != null && Globals.program.getBackStepper() != null
            && Globals.program.getBackStepper().enabled());
    }

    public String getMemoryConfiguration() {
        return get(MemoryConfiguration);
    }
    
    public boolean getStartAtMain() {
        return getBooleanValue(StartAtMain);
    }

}