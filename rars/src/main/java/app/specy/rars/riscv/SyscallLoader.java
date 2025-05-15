package app.specy.rars.riscv;

import app.specy.rars.Globals;
import app.specy.rars.riscv.io.RISCVIO;
import app.specy.rars.riscv.syscalls.*;
import app.specy.rars.util.FilenameFinder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/*
Copyright (c) 2003-2006,  Pete Sanderson and Kenneth Vollmar

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
 * This class provides functionality to bring external Syscall definitions
 * into RARS.  This permits anyone with knowledge of the Rars public interfaces,
 * in particular of the Memory and Register classes, to write custom RISCV syscall
 * functions. This is adapted from the ToolLoader class, which is in turn adapted
 * from Bret Barker's GameServer class from the book "Developing Games In Java".
 */

public class SyscallLoader {

    private RISCVIO io;

    public SyscallLoader(RISCVIO io) {
        this.io = io;
        loadSyscalls();
    }

    private static ArrayList<AbstractSyscall> syscallList;

    SyscallLoader add(AbstractSyscall syscall) {
        syscallList.add(syscall);
        return this;
    }

    private void loadSyscalls() {
        syscallList = new ArrayList<AbstractSyscall>();
        add(new SyscallClose());
        add(new SyscallConfirmDialog(io));
        add(new SyscallExit());
        add(new SyscallExit2());
        add(new SyscallInputDialogDouble(io));
        add(new SyscallInputDialogFloat(io));
        add(new SyscallInputDialogInt(io));
        add(new SyscallInputDialogString(io));
        add(new SyscallMessageDialog(io));
        add(new SyscallMessageDialogDouble(io));
        add(new SyscallMessageDialogFloat(io));
        add(new SyscallMessageDialogInt(io));
        add(new SyscallMessageDialogString(io));

        add(new SyscallOpen());
        add(new SyscallPrintChar());
        add(new SyscallPrintDouble());
        add(new SyscallPrintFloat());
        add(new SyscallPrintInt());
        add(new SyscallPrintIntBinary());
        add(new SyscallPrintIntHex());
        add(new SyscallPrintIntUnsigned());
        add(new SyscallPrintString());
        add(new SyscallRandDouble());
        add(new SyscallRandFloat());
        add(new SyscallRandInt());
        add(new SyscallRandIntRange());
        add(new SyscallRead());
        add(new SyscallWrite());
        add(new SyscallReadChar());
        add(new SyscallReadDouble());
        add(new SyscallReadFloat());
        add(new SyscallReadInt());
        add(new SyscallReadString());
        add(new SyscallSbrk());
        add(new SyscallTime());

        add(new SyscallGetCWD());
        add(new SyscallLSeek());

        syscallList = processSyscallNumberOverrides(syscallList);
    }


    // Loads system call numbers from Syscall.properties
    private static ArrayList<AbstractSyscall> processSyscallNumberOverrides(ArrayList<AbstractSyscall> syscallList) {
        ArrayList<SyscallNumberOverride> overrides = new Globals().getSyscallOverrides();
        if (syscallList.size() != overrides.size()) {
            System.out.println("Error: the number of entries in the config file does not match the number of syscalls loaded");
            System.out.println("Ensure there is a Syscall.properties file in the directory you are executing if you are a developer");
            System.exit(0);
        }
        for (SyscallNumberOverride override : overrides) {
            boolean match = false;
            for (AbstractSyscall syscall : syscallList) {
                if (syscall.getNumber() == override.getNumber()) {
                    System.out.println("Duplicate service number: " + syscall.getNumber() + " already registered to " +
                            findSyscall(syscall.getNumber()).getName());
                    System.exit(0);
                }
                if (override.getName().equals(syscall.getName())) {
                    if (syscall.getNumber() != -1) {
                        System.out.println("Error: " + syscall.getName() + " was assigned a numebr twice in the config file");
                        System.exit(0);
                    }
                    if (override.getNumber() < 0) {
                        System.out.println("Error: " + override.getName() + " was assigned a negative number");
                        System.exit(0);
                    }
                    // we have a match to service name, assign new number
                    syscall.setNumber(override.getNumber());
                    match = true;
                }
            }
            if (!match) {
                System.out.println("Error: syscall name '" + override.getName() +
                        "' in config file does not match any name in syscall list");
                System.exit(0);
            }
        }
        return syscallList;
    }

    /*
     * Method to find Syscall object associated with given service number.
     * Returns null if no associated object found.
     */
    public static AbstractSyscall findSyscall(int number) {
        // linear search is OK since number of syscalls is small.
        for (AbstractSyscall service : syscallList) {
            if (service.getNumber() == number) {
                return service;
            }
        }
        return null;
    }

    public static ArrayList<AbstractSyscall> getSyscallList() {
        return syscallList;
    }
}
