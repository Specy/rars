package app.specy.rars.riscv.syscalls;

import app.specy.rars.ExitingException;
import app.specy.rars.Globals;
import app.specy.rars.ProgramStatement;
import app.specy.rars.riscv.hardware.AddressErrorException;
import app.specy.rars.riscv.hardware.RegisterFile;

import app.specy.rars.util.Utf8;

import java.io.ByteArrayOutputStream;

/*
Copyright (c) 2003-2017,  Pete Sanderson,Benjamin Landers and Kenneth Vollmar

Developed by Pete Sanderson (psanderson@otterbein.edu),
Benjamin Landers (benjaminrlanders@gmail.com),
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
 * Small helper class to wrap getting null terminated strings from memory. The bytes are decoded
 * from UTF-8 the way Java 21 decodes them, malformed sequences included.
 */
public class NullString {
    /**
     * Just a wrapper around #String get(ProgramStatement, String) which passes in the default "a0"
     */
    public static String get(ProgramStatement statement) throws ExitingException {
        return get(statement, "a0");
    }

    /**
     * Reads a NULL terminated string from memory starting at the address in reg
     *
     * @param statement the program statement this was called from (used for error handling)
     * @param reg       The name of the register for the address of the string
     * @return the string read from memory
     * @throws ExitingException if it hits a #AddressErrorException
     */
    public static String get(ProgramStatement statement, String reg) throws ExitingException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            int byteAddress = RegisterFile.getValue(reg);
            int value = Globals.memory.getByte(byteAddress);
            while (value != 0) { // until null terminator
                bytes.write(value);
                byteAddress++;
                value = Globals.memory.getByte(byteAddress);
            }
        } catch (AddressErrorException e) {
            throw new ExitingException(statement, e);
        }
        return Utf8.decode(bytes.toByteArray());
    }
}
