package app.specy.rars;

import app.specy.rars.assembler.SymbolTable;
import app.specy.rars.config.ConfigProperties;
import app.specy.rars.config.SyscallProperties;
import app.specy.rars.riscv.SyscallLoader;
import app.specy.rars.riscv.hardware.Memory;
import app.specy.rars.riscv.InstructionSet;
import app.specy.rars.riscv.SyscallNumberOverride;

import java.util.*;

/*
Copyright (c) 2003-2008,  Pete Sanderson and Kenneth Vollmar

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
 * Collection of globally-available data structures.
 *
 * @author Pete Sanderson
 * @version August 2003
 */
public class Globals {
    // List these first because they are referenced by methods called at initialization.
    private static ConfigProperties configProperties;


    private static SyscallProperties syscallProperties;
    public static SyscallProperties getSyscallProperties() {
        if (syscallProperties == null) {
            syscallProperties = new SyscallProperties();
        }
        return syscallProperties;
    }

    public static ConfigProperties getConfigProperties() {
        if (configProperties == null) {
            configProperties = new ConfigProperties();
        }
        return configProperties;
    }



    /**
     * The set of implemented instructions.
     **/
    public static InstructionSet instructionSet;
    /**
     * the program currently being worked with.  Used by GUI only, not command line.
     **/
    public static RISCVprogram program;
    /**
     * Symbol table for file currently being assembled.
     **/
    public static SymbolTable symbolTable;
    /**
     * Simulated memory component.
     **/
    public static Memory memory;
    /**
     * Lock variable used at head of synchronized block to guard memory and registers
     **/
    /**
     * Flag to determine whether or not to produce internal debugging information.
     **/
    public static boolean debug = false;
    /**
     * Object that contains various settings that can be accessed modified internally.
     **/
    static Settings settings;
    /**
     * String to GUI's RunI/O text area when echoing user input from pop-up dialog.
     */
    public static String userInputAlert = "**** user input : ";
    /**
     * Path to folder that contains images
     */
    // The leading "/" in filepath prevents package name from being pre-pended.
    public static final String imagesPath = "/images/";
    /**
     * Path to folder that contains help text
     */
    public static final String helpPath = "/help/";
    /* Flag that indicates whether or not instructionSet has been initialized. */
    private static boolean initialized = false;
    /**
     * The current version number. Can't wait for "initialize()" call to get it.
     */
    public static final String version = "1.6";

    /**
     * Maximum length of scrolled message window (RARS Messages and Run I/O)
     */
    public static final int maximumMessageCharacters = getMessageLimit();
    /**
     * Maximum number of assembler errors produced by one assemble operation
     */
    public static final int maximumErrorMessages = getErrorLimit();
    /**
     * Maximum number of back-step operations to buffer
     */
    public static final int maximumBacksteps = getBackstepLimit();
    /**
     * Copyright years
     */
    public static final String copyrightYears = getCopyrightYears();
    /**
     * Copyright holders
     */
    public static final String copyrightHolders = getCopyrightHolders();
    /**
     * Placeholder for non-printable ASCII codes
     */
    public static final String ASCII_NON_PRINT = getAsciiNonPrint();
    /**
     * Array of strings to display for ASCII codes in ASCII display of data segment. ASCII code 0-255 is array index.
     */
    public static final String[] ASCII_TABLE = getAsciiStrings();
    /**
     * Exit code -- useful with SYSCALL 17 when running from command line (not GUI)
     */
    public static int exitCode = 0;

    public static boolean runSpeedPanelExists = false;

    private static String getCopyrightYears() {
        return "2003-2019";
    }

    private static String getCopyrightHolders() {
        return "Pete Sanderson and Kenneth Vollmar";
    }

    public static Settings getSettings() {
        return settings;
    }



    public static InstructionSet getInstructionSet(){
        return instructionSet;
    }

    /**
     * Method called once upon system initialization to create the global data structures.
     **/

    public static void initialize() {
        if (!initialized) {
            memory = Memory.getInstance();  //clients can use Memory.getInstance instead of Globals.memory
            symbolTable = new SymbolTable("global");
            settings = new Settings();
            instructionSet = new InstructionSet();
            instructionSet.populate();
            initialized = true;
            debug = false;
            memory.clear(); // will establish memory configuration from setting
        }
    }

    // Read byte limit of Run I/O or RARS Messages text to buffer.
    private static int getMessageLimit() {
        return Globals.getConfigProperties().getIntegerValue(ConfigProperties.MessageLimit);
    }

    // Read limit on number of error messages produced by one assemble operation.
    private static int getErrorLimit() {
        return Globals.getConfigProperties().getIntegerValue(ConfigProperties.ErrorLimit);
    }

    // Read backstep limit (number of operations to buffer) from properties file.
    private static int getBackstepLimit() {
        return Globals.getConfigProperties().getIntegerValue(ConfigProperties.BackstepLimit);
    }

    // Read ASCII default display character for non-printing characters, from properties file.
    public static String getAsciiNonPrint() {
        String anp = Globals.getConfigProperties().get(ConfigProperties.AsciiNonPrint);
        return (anp == null) ? "." : ((anp.equals("space")) ? " " : anp);
    }

    // Read ASCII strings for codes 0-255, from properties file. If string
    // value is "null", substitute value of ASCII_NON_PRINT.  If string is
    // "space", substitute string containing one space character.
    public static String[] getAsciiStrings() {
        String let = Globals.getConfigProperties().get(ConfigProperties.AsciiTable);
        String placeHolder = getAsciiNonPrint();
        if(let == null){
            // If config isn't loaded, give a decent default value.
            String[] table = new String[((int) '~' )+ 1];
            for (int i = 0; i < table.length; i++) {
                if (i == 0) table[i] = "\0";
                else if (i == '\n') table[i] = "\n";
                else if (i < ' ') table[i] = placeHolder;
                else table[i] = " "+(char) i;
            }
            return table;
        }else {
            String[] lets = let.split(" +");
            int maxLength = 0;
            for (int i = 0; i < lets.length; i++) {
                if (lets[i].equals("null")) lets[i] = placeHolder;
                if (lets[i].equals("space")) lets[i] = " ";
                if (lets[i].length() > maxLength) maxLength = lets[i].length();
            }
            String padding = "        ";
            maxLength++;
            for (int i = 0; i < lets.length; i++) {
                lets[i] = padding.substring(0, maxLength - lets[i].length()) + lets[i];
            }
            return lets;
        }
    }




    /**
     * Read any syscall number assignment overrides from config file.
     *
     * @return ArrayList of SyscallNumberOverride objects
     */
    public ArrayList<SyscallNumberOverride> getSyscallOverrides() {
        ArrayList<SyscallNumberOverride> overrides = new ArrayList<SyscallNumberOverride>();
        Set<String> properties = Globals.getSyscallProperties().keySet();
        for (String key : properties) {
            overrides.add(new SyscallNumberOverride(key, Globals.getSyscallProperties().get(key)));
        }
        return overrides;
    }

}
