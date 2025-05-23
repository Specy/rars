package app.specy.rars.riscv.hardware;

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
 * Object provided to Observers of runtime access to memory or registers.
 * The access types READ and WRITE defined here; use subclasses defined for
 * MemoryAccessNotice and RegisterAccessNotice.  This is abstract class.
 *
 * @author Pete Sanderson
 * @version July 2005
 */


public abstract class AccessNotice {
    /**
     * Indicates the purpose of access was to read.
     */
    public static final int READ = 0;
    /**
     * Indicates the purpose of access was to write.
     */
    public static final int WRITE = 1;

    private int accessType;

    protected AccessNotice(int type) {
        if (type != READ && type != WRITE) {
            throw new IllegalArgumentException();
        }
        accessType = type;
    }

    /**
     * Get the access type: READ or WRITE.
     *
     * @return Access type, either AccessNotice.READ or AccessNotice.WRITE
     */
    public int getAccessType() {
        return accessType;
    }

}