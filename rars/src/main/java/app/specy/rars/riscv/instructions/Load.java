package app.specy.rars.riscv.instructions;

import app.specy.rars.ProgramStatement;
import app.specy.rars.SimulationException;
import app.specy.rars.riscv.BasicInstruction;
import app.specy.rars.riscv.BasicInstructionFormat;
import app.specy.rars.riscv.hardware.AddressErrorException;
import app.specy.rars.riscv.hardware.RegisterFile;

/*
Copyright (c) 2017,  Benjamin Landers

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
 * Base class for all Load instructions
 *
 * @author Benjamin Landers
 * @version June 2017
 */
public abstract class Load extends BasicInstruction {
    public Load(String usage, String description, String funct) {
        super(usage, description, BasicInstructionFormat.I_FORMAT,
                "ssssssssssss ttttt " + funct + " fffff 0000011");
    }
    public Load(String usage, String description, String funct, boolean rv64) {
        super(usage, description, BasicInstructionFormat.I_FORMAT,
                "ssssssssssss ttttt " + funct + " fffff 0000011",rv64);

    }


    public void simulate(ProgramStatement statement) throws SimulationException {
        int[] operands = statement.getOperands();
        operands[1] = (operands[1] << 20) >> 20;
        try {
            RegisterFile.updateRegister(operands[0], load(RegisterFile.getValue(operands[2]) + operands[1]));
        } catch (AddressErrorException e) {
            throw new SimulationException(statement, e);
        }
    }

    /**
     * @param address the address to load from
     * @return The value to store to the register
     */
    protected abstract long load(int address) throws AddressErrorException;
}