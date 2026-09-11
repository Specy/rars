package app.specy.rars.riscv.hardware;

import app.specy.rars.util.Binary;

import java.util.HashMap;
import java.util.Map;
import java.util.Observer;

/*
Copyright (c) 2003-2017,  Pete Sanderson, Benjamin Landers and Kenneth Vollmar

Developed by Pete Sanderson (psanderson@otterbein.edu),
Benjamin Landers (benjaminrlanders@gmail.com)
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
 * Helper class for RegisterFile, FPRegisterFile, and CSRFile
 * <p>
 * Much of the implementation was ripped directly from RegisterFile
 *
 * @author Benjamin Landers
 * @version June 2017
 */
public class RegisterBlock {
    private final Register[] regFile;
    private final char prefix;
    /** Registers by name, and by number in a table indexed directly to keep the lookup allocation free. */
    private final Map<String, Register> byName;
    private final Register[] byNumber;

    protected RegisterBlock(char prefix, Register[] registers) {
        this.prefix = prefix;
        this.regFile = registers;
        // The block is fixed at construction, so both lookups are indexed once here instead of
        // scanning it on every access. The simulator reads and writes the cycle, time and instret
        // CSRs on every instruction and every instruction operand resolves a register by number,
        // and each of those used to walk the whole block comparing names or numbers.
        this.byName = new HashMap<>();
        int highestNumber = 0;
        for (Register register : registers) {
            if (register == null) continue;
            if (register.getNumber() > highestNumber) highestNumber = register.getNumber();
        }
        this.byNumber = new Register[highestNumber + 1];
        for (Register register : registers) {
            if (register == null) continue;
            // first match wins, which is what scanning the block in order did
            this.byName.putIfAbsent(register.getName(), register);
            if (this.byNumber[register.getNumber()] == null) {
                this.byNumber[register.getNumber()] = register;
            }
        }
    }

    /**
     * Method for displaying the register values for debugging.
     **/
    public void showRegisters() {
        for (Register r : regFile) {
            System.out.println("Name: " + r.getName());
            System.out.println("Number: " + r.getNumber());
            System.out.println("Value: " + r.getValue());
            System.out.println("");
        }
    }

    /**
     * This method updates the register value to val
     *
     * @param r   Register to set the value of.
     * @param val The desired value for the register.
     **/
    public long updateRegister(Register r, long val) {
        if (r == null) return 0;
        return r.setValue(val);
    }

    public long updateRegister(int num, long val) {
        return updateRegister(getRegister(num), val);
    }

    public long updateRegister(String name, long val) {
        return updateRegister(getRegister(name), val);
    }

    /**
     * Returns the value of the register.
     *
     * @param num The register's number.
     * @return The value of the given register.
     **/
    public long getValue(int num) {
        return getRegister(num).getValue();
    }

    /**
     * Returns the value of the register.
     *
     * @param name The register's name.
     * @return The value of the given register.
     **/
    public long getValue(String name) {
        return getRegister(name).getValue();
    }

    /**
     * Get a register from a number
     *
     * @param num the number to search for
     * @return the register for num or null if none exists
     */
    public Register getRegister(int num) {
        return (num >= 0 && num < byNumber.length) ? byNumber[num] : null;
    }

    /**
     * Get register object corresponding to given name.  If no match, return null.
     *
     * @param name The register name, either in (prefix)(number) format or a direct name.
     * @return The register object,or null if not found.
     **/
    public Register getRegister(String name) {
        if(name.length() < 2) return null;

        // Handle a direct name
        Register direct = byName.get(name);
        if (direct != null) {
            return direct;
        }
        // Handle prefix case
        if (name.charAt(0) == prefix) {
            if(name.charAt(1) == 0) { // Ensure that it is a normal decimal number
                if(name.length() > 2) return null;
                return getRegister(0);
            }

            Integer num = Binary.stringToIntFast(name.substring(1));
            if(num == null) return null;
            return getRegister(num);
        }
        return null;
    }

    /**
     * For returning the set of registers.
     *
     * @return The set of registers.
     **/

    public Register[] getRegisters() {
        return regFile;
    }

    /**
     * Method to reinitialize the values of the registers.
     **/
    public void resetRegisters() {
        for (Register r : regFile) {
            r.resetValue();
        }
    }

    /**
     * Each individual register is a separate object and Observable.  This handy method
     * will add the given Observer to each one.  Currently does not apply to Program
     * Counter.
     */
    public void addRegistersObserver(Observer observer) {
        for (Register r : regFile) {
            r.addObserver(observer);
        }
    }

    /**
     * Each individual register is a separate object and Observable.  This handy method
     * will delete the given Observer from each one.  Currently does not apply to Program
     * Counter.
     */
    public void deleteRegistersObserver(Observer observer) {
        for (Register r : regFile) {
            r.deleteObserver(observer);
        }
    }
}
