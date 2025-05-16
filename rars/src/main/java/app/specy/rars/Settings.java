package app.specy.rars;

import app.specy.rars.config.SettingsProperties;

import java.util.HashMap;
import java.util.Observable;

/*
Copyright (c) 2003-2013,  Pete Sanderson and Kenneth Vollmar

Developed by Pete Sanderson (psanderson@otterbein.edu)
and Kenneth Vollmar (kenvollmar@missouristate.edu)

Permission is hereby granted, free of charge, to any person obtaining 
a copy of this software and associated documentation files (the 
"Software"), to deal in the Software without restriction, including 
without limitation the rights to use, copy, modify, merge, publish, 
distribute, sublicense, and/or sell copies of the Software, and to 
permit persons to whom the Software is furnished to do so, subject 
to the following conditions:

The above copyright notice and this permission notice shall be 
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, 
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF 
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR 
ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF 
CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION 
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

(MIT license, http://www.opensource.org/licenses/mit-license.html)
 */

/**
 * Contains various IDE settings.  Persistent settings are maintained for the
 * current user and on the current machine using
 * Java's Preference objects.  Failing that, default setting values come from
 * Settings.properties file.  If both of those fail, default values come from
 * static arrays defined in this class.  The latter can can be modified prior to
 * instantiating Settings object.
 * <p>
 * NOTE: If the Preference objects fail due to security exceptions, changes to
 * settings will not carry over from one RARS session to the next.
 * <p>
 * Actual implementation of the Preference objects is platform-dependent.
 * For Windows, they are stored in Registry.  To see, run regedit and browse to:
 * HKEY_CURRENT_USER\Software\JavaSoft\Prefs\rars
 *
 * @author Pete Sanderson
 **/

public class Settings extends Observable {
    /* Properties file used to hold default settings. */

    private static SettingsProperties settingsProperties = new SettingsProperties();

    // BOOLEAN SETTINGS...
    public enum Bool {
        /**
         * Flag to determine whether or not program being assembled is limited to
         * basic instructions and formats.
         */
        EXTENDED_ASSEMBLER_ENABLED("ExtendedAssembler", true),
        /**
         * Flag to determine whether or not a file is immediately and automatically assembled
         * upon opening. Handy when using externa editor like mipster.
         */
        ASSEMBLE_ON_OPEN("AssembleOnOpen", false),
        /**
         *  Flag to determine whether all files open currently source file will be assembled when assembly is selected.
         */
        ASSEMBLE_OPEN("AssembleOpen", false),
        /**
         * Flag to determine whether files in the directory of the current source file will be assembled when assembly is selected.
         */
        ASSEMBLE_ALL("AssembleAll", false),

        /**
         * Default visibilty of label window (symbol table).  Default only, dynamic status
         * maintained by ExecutePane
         */
        LABEL_WINDOW_VISIBILITY("LabelWindowVisibility", false),
        /**
         * Default setting for displaying addresses and values in hexidecimal in the Execute
         * pane.
         */
        DISPLAY_ADDRESSES_IN_HEX("DisplayAddressesInHex", true),
        DISPLAY_VALUES_IN_HEX("DisplayValuesInHex", true),
        /**
         * Flag to determine whether the currently selected exception handler source file will
         * be included in each assembly operation.
         */
        EXCEPTION_HANDLER_ENABLED("LoadExceptionHandler", false),
        /**
         * Flag to determine whether or not the editor will display line numbers.
         */
        EDITOR_LINE_NUMBERS_DISPLAYED("EditorLineNumbersDisplayed", true),
        /**
         * Flag to determine whether or not assembler warnings are considered errors.
         */
        WARNINGS_ARE_ERRORS("WarningsAreErrors", false),
        /**
         * Flag to determine whether or not to display and use program arguments
         */
        PROGRAM_ARGUMENTS("ProgramArguments", false),
        /**
         * Flag to control whether or not highlighting is applied to data segment window
         */
        DATA_SEGMENT_HIGHLIGHTING("DataSegmentHighlighting", true),
        /**
         * Flag to control whether or not highlighting is applied to register windows
         */
        REGISTERS_HIGHLIGHTING("RegistersHighlighting", true),
        /**
         * Flag to control whether or not assembler automatically initializes program counter to 'main's address
         */
        START_AT_MAIN("StartAtMain", false),
        /**
         * Flag to control whether or not editor will highlight the line currently being edited
         */
        EDITOR_CURRENT_LINE_HIGHLIGHTING("EditorCurrentLineHighlighting", true),
        /**
         * Flag to control whether or not editor will provide popup instruction guidance while typing
         */
        POPUP_INSTRUCTION_GUIDANCE("PopupInstructionGuidance", true),
        /**
         * Flag to control whether or not simulator will use popup dialog for input syscalls
         */
        POPUP_SYSCALL_INPUT("PopupSyscallInput", false),
        /**
         * Flag to control whether or not to use generic text editor instead of language-aware styled editor
         */
        GENERIC_TEXT_EDITOR("GenericTextEditor", false),
        /**
         * Flag to control whether or not language-aware editor will use auto-indent feature
         */
        AUTO_INDENT("AutoIndent", true),
        /**
         * Flag to determine whether a program can write binary code to the text or data segment and
         * execute that code.
         */
        SELF_MODIFYING_CODE_ENABLED("SelfModifyingCode", false),
        /**
         * Flag to determine whether a program uses rv64i instead of rv32i
         */
        RV64_ENABLED("rv64Enabled", false),
        /**
         * Flag to determine whether to calculate relative paths from the current working directory
         * or from the RARS executable path.
        */
        DERIVE_CURRENT_WORKING_DIRECTORY("DeriveCurrentWorkingDirectory", false);

        // TODO: add option for turning off user trap handling and interrupts
        private String name;
        private boolean value;

        Bool(String n, boolean v) {
            name = n;
            value = v;
        }

        boolean getDefault() {
            return value;
        }

        void setDefault(boolean v) {
            value = v;
        }

        String getName() {
            return name;
        }
    }
    // STRING SETTINGS.  Each array position has associated name.
    /**
     * Current specified exception handler file (a RISCV assembly source file)
     */
    public static final int EXCEPTION_HANDLER = 0;
    /**
     * Order of text segment table columns
     */
    public static final int TEXT_COLUMN_ORDER = 1;
    /**
     * State for sorting label window display
     */
    public static final int LABEL_SORT_STATE = 2;
    /**
     * Identifier of current memory configuration
     */
    public static final int MEMORY_CONFIGURATION = 3;
    /**
     * Caret blink rate in milliseconds, 0 means don't blink.
     */
    public static final int CARET_BLINK_RATE = 4;
    /**
     * Editor tab size in characters.
     */
    public static final int EDITOR_TAB_SIZE = 5;
    /**
     * Number of letters to be matched by editor's instruction guide before popup generated (if popup enabled)
     */
    public static final int EDITOR_POPUP_PREFIX_LENGTH = 6;
    // Match the above by position.
    private static final String[] stringSettingsKeys = {"ExceptionHandler", "TextColumnOrder", "LabelSortState", "MemoryConfiguration", "CaretBlinkRate", "EditorTabSize", "EditorPopupPrefixLength"};

    /**
     * Last resort default values for String settings;
     * will use only if neither the Preferences nor the properties file work.
     * If you wish to change, do so before instantiating the Settings object.
     * Must match key by list position.
     */
    private static String[] defaultStringSettingsValues = {"", "0 1 2 3 4", "0", "", "500", "8", "2"};




    private HashMap<Bool, Boolean> booleanSettingsValues;
    private String[] stringSettingsValues;

    /**
     * Create Settings object and set to saved values.  If saved values not found, will set
     * based on defaults stored in Settings.properties file.  If file problems, will set based
     * on defaults stored in this class.
     */

    public Settings() {
        booleanSettingsValues = new HashMap<>();
        stringSettingsValues = new String[stringSettingsKeys.length];
        initialize();
    }


    /**
     * Return whether backstepping is permitted at this time.  Backstepping is ability to undo execution
     * steps one at a time.  Available only in the IDE.  This is not a persistent setting and is not under
     * RARS user control.
     *
     * @return true if backstepping is permitted, false otherwise.
     */
    public boolean getBackSteppingEnabled() {
        return (Globals.program != null && Globals.program.getBackStepper() != null && Globals.program.getBackStepper().enabled());
    }


    /**
     * Reset settings to default values, as described in the constructor comments.
     */
    public void reset() {
        initialize();
    }



    ////////////////////////////////////////////////////////////////////////
    //  Setting Getters
    ////////////////////////////////////////////////////////////////////////


    /**
     * Fetch value of a boolean setting given its identifier.
     *
     * @param setting the setting to fetch the value of
     * @return corresponding boolean setting.
     * @throws IllegalArgumentException if identifier is invalid.
     */
    public boolean getBooleanSetting(Bool setting) {
        if (booleanSettingsValues.containsKey(setting)) {
            return booleanSettingsValues.get(setting);
        } else {
            throw new IllegalArgumentException("Invalid boolean setting ID");
        }
    }

    /**
     * Name of currently selected exception handler file.
     *
     * @return String pathname of current exception handler file, empty if none.
     */
    public String getExceptionHandler() {
        return stringSettingsValues[EXCEPTION_HANDLER];
    }

    /**
     * Returns identifier of current built-in memory configuration.
     *
     * @return String identifier of current built-in memory configuration, empty if none.
     */
    public String getMemoryConfiguration() {
        return stringSettingsValues[MEMORY_CONFIGURATION];
    }



    /**
     * Set value of a boolean setting given its id and the value.
     *
     * @param setting setting to set the value of
     * @param value boolean value to store
     * @throws IllegalArgumentException if identifier is not valid.
     */
    public void setBooleanSetting(Bool setting, boolean value) {
        if (booleanSettingsValues.containsKey(setting)) {
            internalSetBooleanSetting(setting, value);
        } else {
            throw new IllegalArgumentException("Invalid boolean setting ID");
        }
    }

    /**
     * Temporarily establish boolean setting.  This setting will NOT be written to persisent
     * store!  Currently this is used only when running RARS from the command line
     *
     * @param setting the setting to set the value of
     * @param value True to enable the setting, false otherwise.
     */
    public void setBooleanSettingNonPersistent(Bool setting, boolean value) {
        if (booleanSettingsValues.containsKey(setting)) {
            booleanSettingsValues.put(setting, value);
        } else {
            throw new IllegalArgumentException("Invalid boolean setting ID");
        }
    }

    /**
     * Set name of exception handler file and write it to persistent storage.
     *
     * @param newFilename name of exception handler file
     */
    public void setExceptionHandler(String newFilename) {
        setStringSetting(EXCEPTION_HANDLER, newFilename);
    }

    /**
     * Store the identifier of the memory configuration.
     *
     * @param config A string that identifies the current built-in memory configuration
     */

    public void setMemoryConfiguration(String config) {
        setStringSetting(MEMORY_CONFIGURATION, config);
    }

    // Initialize settings to default values.
    // Strategy: First set from properties file.
    //           If that fails, set from array.
    //           In either case, use these values as defaults in call to Preferences.

    private void initialize() {
        applyDefaultSettings();
        readSettingsFromProperties();
    }

    // Default values.  Will be replaced if available from property file or Preferences object.
    private void applyDefaultSettings() {
        for (Bool setting : Bool.values()) {
            booleanSettingsValues.put(setting, setting.getDefault());
        }
        for (int i = 0; i < stringSettingsValues.length; i++) {
            stringSettingsValues[i] = defaultStringSettingsValues[i];
        }
    }

    // Used by all the boolean setting "setter" methods.
    private void internalSetBooleanSetting(Bool setting, boolean value) {
        if (value != booleanSettingsValues.get(setting)) {
            booleanSettingsValues.put(setting, value);
            setChanged();
            notifyObservers();
        }
    }

    // Used by setter method(s) for string-based settings (initially, only exception handler name)
    private void setStringSetting(int settingIndex, String value) {
        stringSettingsValues[settingIndex] = value;
    }


    // Establish the settings from the given properties file.  Return true if it worked,
    // false if it didn't.  Note the properties file exists only to provide default values
    // in case the Preferences fail or have not been recorded yet.
    //
    // Any settings successfully read will be stored in both the xSettingsValues and
    // defaultXSettingsValues arrays (x=boolean,string,color).  The latter will overwrite the
    // last-resort default values hardcoded into the arrays above.
    //
    // NOTE: If there is NO ENTRY for the specified property, Globals.getPropertyEntry() returns
    // null.  This is no cause for alarm.  It will occur during system development or upon the
    // first use of a new RARS release in which new settings have been defined.
    // In that case, this method will NOT make an assignment to the settings array!
    // So consider it a precondition of this method: the settings arrays must already be
    // initialized with last-resort default values.
    private boolean readSettingsFromProperties() {
        String settingValue;
        try {
            for (Bool setting : Bool.values()) {
                settingValue = settingsProperties.get(setting.getName());
                if (settingValue != null) {
                    boolean value = Boolean.valueOf(settingValue);
                    setting.setDefault(value);
                    booleanSettingsValues.put(setting, value);
                }
            }
            for (int i = 0; i < stringSettingsKeys.length; i++) {
                settingValue = settingsProperties.get(stringSettingsKeys[i]);
                if (settingValue != null)
                    stringSettingsValues[i] = defaultStringSettingsValues[i] = settingValue;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }




}
