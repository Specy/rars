package app.specy.rars.riscv.hardware;

import app.specy.rars.Globals;

import java.util.Observer;

/*
Copyright (c) 2017-2019,  Benjamin Landers

Developed by Benjamin Landers (benjaminrlanders@gmail.com)

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
 * Represents the implemented control and status registers. The main classes are fcsr (for floating point errors),
 * timers, and interrupt handling.
 *
 * @author Benjamin Landers
 * @version August 2017
 **/

public class ControlAndStatusRegisterFile {
    public static final int EXTERNAL_INTERRUPT = 0x100;
    public static final int TIMER_INTERRUPT = 0x10;
    public static final int SOFTWARE_INTERRUPT = 0x1;


    public static final int INTERRUPT_ENABLE = 0x1;
    
    private static final RegisterBlock instance;

    static {
        // TODO: consider making time, cycle and instret 64 bit registers which then are linked to by *h
        // Remember to update the window tooltips when adding a CSR
        Register[] tmp = {
                new MaskedRegister("ustatus", 0x000, 0,~0x11),
                null, // fflags
                null, // frm
                new MaskedRegister("fcsr", 0x003, 0, ~0xFF),
                new Register("uie", 0x004, 0),
                new Register("utvec", 0x005, 0),
                new Register("uscratch", 0x040, 0),
                new Register("uepc", 0x041, 0),
                new Register("ucause", 0x042, 0),
                new Register("utval", 0x043, 0),
                new Register("uip", 0x044, 0),
                new ReadOnlyRegister("cycle", 0xC00, 0),
                new ReadOnlyRegister("time", 0xC01, 0),
                new ReadOnlyRegister("instret",0xC02, 0),
                null, // cycleh
                null, // timeh
                null, // instreth
        };
        tmp[1] = new LinkedRegister("fflags", 0x001, tmp[3], 0x1F);
        tmp[2] = new LinkedRegister("frm", 0x002, tmp[3], 0xE0);

        tmp[14] = new LinkedRegister("cycleh", 0xC80,tmp[11], 0xFFFFFFFF_00000000L);
        tmp[15] = new LinkedRegister("timeh", 0xC81, tmp[12],0xFFFFFFFF_00000000L);
        tmp[16] = new LinkedRegister("instreth",0xC82, tmp[13],0xFFFFFFFF_00000000L);
        instance = new RegisterBlock('_', tmp); // prefix not used
    }

    /**
     * This method updates the register value
     *
     * @param num Number of register to set the value of.
     * @param val The desired value for the register.
     * @return old value in register prior to update
     **/
    public static boolean updateRegister(int num, long val) {
        if (instance.getRegister(num) instanceof ReadOnlyRegister) {
            return true;
        }
        // TODO: do something to better handle the h csrs
        if (num >= 0xC80 && num <= 0xC82) {
            return true;
        }
        if ((Globals.getSettings().getBackSteppingEnabled())) {
            Globals.program.getBackStepper().addControlAndStatusRestore(num, instance.updateRegister(num, val));
        } else {
            instance.updateRegister(num, val);
        }
        return false;
    }

    /**
     * This method updates the register value
     *
     * @param name Name of register to set the value of.
     * @param val  The desired value for the register.
     * @return old value in register prior to update
     **/
    public static void updateRegister(String name, long val) {
        updateRegister(instance.getRegister(name).getNumber(), val);
    }

    /**
     * This method updates the register value silently and bypasses read only
     *
     * @param num Number of register to set the value of.
     * @param val  The desired value for the register.
     * @return old value in register prior to update
     **/
    /** Names of the two counters the simulator advances on every instruction. */
    public static final String CYCLE = "cycle";
    public static final String INSTRET = "instret";

    public static void updateRegisterBackdoor(int num, long val) {
        updateRegisterBackdoor(instance.getRegister(num), val);
    }

    /**
     * As above, for a register the caller already holds. The simulator writes the cycle, instret
     * and time counters on every instruction, so it resolves them once rather than by name.
     *
     * @param register Register to set the value of.
     * @param val      The desired value for the register.
     **/
    public static void updateRegisterBackdoor(Register register, long val) {
        long old = register.setValueBackdoor(val);
        // Writing a register the value it already holds is not a change, and its undo entry would
        // restore that same value, so none is recorded. The time counter reports milliseconds and
        // is written every instruction, so nearly all of its writes are such no-ops; recording them
        // spent the whole undo history on entries that undo nothing.
        if (old == val) return;
        if ((Globals.getSettings().getBackSteppingEnabled())) {
            Globals.program.getBackStepper().addControlAndStatusBackdoor(register.getNumber(), old);
        }
    }

    /**
     * This method updates the register value silently and bypasses read only
     *
     * @param name Name of register to set the value of.
     * @param val  The desired value for the register.
     * @return old value in register prior to update
     **/
    public static void updateRegisterBackdoor(String name, long val) {
        updateRegisterBackdoor(instance.getRegister(name), val);
    }

    /**
     * ORs a register with a value
     *
     * @param num Number of register to change
     * @param val The value to OR with
     **/
    public static boolean orRegister(int num, long val) {
        return updateRegister(num, instance.getValue(num) | val);
    }

    /**
     * ORs a register with a value
     *
     * @param name Name of register to change
     * @param val  The value to OR with
     **/
    public static void orRegister(String name, long val) {
        updateRegister(name, instance.getValue(name) | val);
    }

    /**
     * Clears bits from a register according to a value
     *
     * @param num Number of register to change
     * @param val The value to clear by
     **/
    public static boolean clearRegister(int num, long val) {
        return updateRegister(num, instance.getValue(num) & ~val);
    }

    /**
     * Clears bits from a register according to a value
     *
     * @param name Name of register to change
     * @param val  The value to clear by
     **/
    public static void clearRegister(String name, long val) {
        updateRegister(name, instance.getValue(name) & ~val);
    }

    /**
     * Returns the value of the register
     *
     * @param num The register number.
     * @return The value of the given register.  0 for non-implemented registers
     **/

    public static int getValue(int num) {
        return (int)instance.getValue(num);
    }

    /**
     * Returns the full value of the register
     *
     * @param num The register number.
     * @return The value of the given register.  0 for non-implemented registers
     **/

    public static long getValueLong(int num) {
        return instance.getValue(num);
    }
    /**
     * Returns the value of the register
     *
     * @param name The register's name
     * @return The value of the given register.  0 for non-implemented registers
     **/

    public static int getValue(String name) {
        return (int)instance.getValue(name);
    }

    /**
     * Returns the value of the register without notifying observers
     *
     * @param name The register's name
     * @return The value of the given register.  0 for non-implemented registers
     **/

    public static long getValueNoNotify(String name) {
        return instance.getRegister(name).getValueNoNotify();
    }

    /**
     * For returning the set of registers.
     *
     * @return The set of registers.
     **/

    public static Register[] getRegisters() {
        return instance.getRegisters();
    }


    /**
     * ControlAndStatusRegisterFile implements a wide range of register numbers that don't math the position in the underlying array
     *
     * @param r the CSR
     * @return the list position of given register, -1 if not found.
     **/

    public static int getRegisterPosition(Register r) {
        Register[] registers = instance.getRegisters();
        for (int i = 0; i < registers.length; i++) {
            if (registers[i] == r) {
                return i;
            }
        }
        return -1;
    }

    
    /**
     * Adds one to the cycle and instret counters and records a single undo entry for both, which is
     * what the simulator does once per instruction. Neither counter has any other writer: both are
     * read only to programs, so this increment is the whole of their history.
     *
     * The caller passes the address of the instruction being counted. The BackStepper's own `pc()`
     * cannot be used here: it reads the program counter and subtracts one instruction, which names
     * the executing instruction only while the instruction did not change the program counter, and
     * these counters are advanced after it has.
     */
    public static void incrementCounters(Register cycle, Register instret, int pc) {
        cycle.setValueBackdoor(cycle.getValueNoNotify() + 1);
        instret.setValueBackdoor(instret.getValueNoNotify() + 1);
        if (Globals.getSettings().getBackSteppingEnabled()) {
            Globals.program.getBackStepper().addControlAndStatusCountersDecrement(pc);
        }
    }

    /** Undoes one {@link #incrementCounters} step. */
    public static void decrementCounters() {
        Register cycle = instance.getRegister(CYCLE), instret = instance.getRegister(INSTRET);
        cycle.setValueBackdoor(cycle.getValueNoNotify() - 1);
        instret.setValueBackdoor(instret.getValueNoNotify() - 1);
    }

    public static Register getRegister(String name) {
        return instance.getRegister(name);
    }

    /**
     * Method to reinitialize the values of the registers.
     **/

    public static void resetRegisters() {
        instance.resetRegisters();
    }

    /**
     * Each individual register is a separate object and Observable.  This handy method
     * will add the given Observer to each one.
     */
    public static void addRegistersObserver(Observer observer) {
        instance.addRegistersObserver(observer);
    }

    /**
     * Each individual register is a separate object and Observable.  This handy method
     * will delete the given Observer from each one.
     */
    public static void deleteRegistersObserver(Observer observer) {
        instance.deleteRegistersObserver(observer);
    }

}
