package app.specy.rars.riscv.syscalls;

import app.specy.rars.ExitingException;
import app.specy.rars.Globals;
import app.specy.rars.ProgramStatement;
import app.specy.rars.riscv.AbstractSyscall;
import app.specy.rars.riscv.hardware.AddressErrorException;
import app.specy.rars.riscv.hardware.FloatingPointRegisterFile;
import app.specy.rars.riscv.hardware.RegisterFile;
import app.specy.rars.riscv.io.RISCVIO;
import app.specy.rars.util.JavaNumberText;



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
 * Service to display a message to user.
 */

public class SyscallMessageDialogDouble extends AbstractSyscall {
    RISCVIO io;

    /**
     * Build an instance of the syscall with its default service number and name.
     */
    public SyscallMessageDialogDouble(RISCVIO io) {
        super("MessageDialogDouble", "Service to display message followed by a double",
                "a0 = address of null-terminated string that is the message to user <br> fa0 = the double", "N/A");
        this.io = io;

    }

    /**
     * System call to display a message to user.
     */
    public void simulate(ProgramStatement statement) throws ExitingException {
        // As UTF-8, as the other dialogs read their message; RARS reads this one a byte per character.
        String message = NullString.get(statement);

        this.io.outputDialog(
                message + JavaNumberText.toString(Double.longBitsToDouble(FloatingPointRegisterFile.getValueLong(10))),
                1);
    }
}
