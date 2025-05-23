package app.specy.rars.riscv;

import app.specy.rars.ProgramStatement;
import app.specy.rars.SimulationException;
import app.specy.rars.riscv.hardware.RegisterFile;
import app.specy.rars.riscv.instructions.*;
import app.specy.rars.riscv.io.RISCVIO;
import app.specy.rars.riscv.syscalls.*;
import app.specy.rars.util.SystemIO;

import java.util.*;

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
 * The list of Instruction objects, each of which represents a RISCV instruction.
 * The instruction may either be basic (translates into binary machine code) or
 * extended (translates into sequence of one or more basic instructions).
 *
 * @author Pete Sanderson and Ken Vollmar
 * @version August 2003-5
 */

public class InstructionSet {
    public static boolean rv64 = false;

    private ArrayList<Instruction> instructionList;
    private ArrayList<MatchMap> opcodeMatchMaps;

    public void setSyscallLoaderIO(RISCVIO io) {
        SyscallLoader.setIO(io);
    }


    /**
     * Creates a new InstructionSet object.
     */
    public InstructionSet() {
        instructionList = new ArrayList<>();
    }

    /**
     * Retrieve the current instruction set.
     */
    public ArrayList<Instruction> getInstructionList() {
        return instructionList;

    }

    /**
     * Adds all instructions to the set.  A given extended instruction may have
     * more than one Instruction object, depending on how many formats it can have.
     *
     * @see Instruction
     * @see BasicInstruction
     * @see ExtendedInstruction
     */
    public void populate() {
        /* Here is where the parade begins.  Every instruction is added to the set here.*/
        instructionList.clear();
        // ////////////////////////////////////   BASIC INSTRUCTIONS START HERE ////////////////////////////////

        addBasicInstructions();
        addPseudoInstructions();
        // Initialization step.  Create token list for each instruction example.  This is
        // used by parser to determine user program correct syntax.
        for (Instruction inst : instructionList) {
            inst.createExampleTokenList();
        }

        HashMap<Integer, HashMap<Integer, BasicInstruction>> maskMap = new HashMap<>();
        ArrayList<MatchMap> matchMaps = new ArrayList<>();
        for (Instruction inst : instructionList) {
            if (inst instanceof BasicInstruction) {
                BasicInstruction basic = (BasicInstruction) inst;
                Integer mask = basic.getOpcodeMask();
                Integer match = basic.getOpcodeMatch();
                HashMap<Integer, BasicInstruction> matchMap = maskMap.get(mask);
                if (matchMap == null) {
                    matchMap = new HashMap<>();
                    maskMap.put(mask, matchMap);
                    matchMaps.add(new MatchMap(mask, matchMap));
                }
                matchMap.put(match, basic);
            }
        }
        Collections.sort(matchMaps);
        this.opcodeMatchMaps = matchMaps;
    }

    public BasicInstruction findByBinaryCode(int binaryInstr) {
        for (MatchMap map : this.opcodeMatchMaps) {
            BasicInstruction ret = map.find(binaryInstr);
            if (ret != null) return ret;
        }
        return null;
    }

    /*  METHOD TO ADD PSEUDO-INSTRUCTIONS
     */
    private void addPseudoInstructions() {

        String pseudoOp, template, token;
        String description;
        StringTokenizer tokenizer;

        List<String> pseudoOps = new ArrayList<>(List.of(PseudoOps.PSEUDO_OPS));
        if (rv64) {
            pseudoOps.addAll(List.of(PseudoOps.PSEUDO_OPS_64));
        }

        for (String line : pseudoOps) {
            // skip over: comment lines, empty lines, lines starting with blank.
            if (!line.startsWith("#") && !line.startsWith(" ")
                    && line.length() > 0) {
                description = "";
                tokenizer = new StringTokenizer(line, ";");
                pseudoOp = tokenizer.nextToken();
                template = "";
                while (tokenizer.hasMoreTokens()) {
                    token = tokenizer.nextToken();
                    if (token.startsWith("#")) {
                        // Optional description must be last token in the line.
                        description = token.substring(1);
                        break;
                    }
                    template = template + token;
                    if (tokenizer.hasMoreTokens()) {
                        template = template + "\n";
                    }
                }
                instructionList.add(new ExtendedInstruction(pseudoOp, template, description));
                //if (firstTemplate != null) System.out.println("\npseudoOp: "+pseudoOp+"\ndefault template:\n"+firstTemplate+"\ncompact template:\n"+template);
            }
        }

    }

    /**
     * Given an operator mnemonic, will return the corresponding Instruction object(s)
     * from the instruction set.  Uses straight linear search technique.
     *
     * @param name operator mnemonic (e.g. addi, sw,...)
     * @return list of corresponding Instruction object(s), or null if not found.
     */
    public ArrayList<Instruction> matchOperator(String name) {
        ArrayList<Instruction> matchingInstructions = null;
        // Linear search for now....
        for (Instruction inst : instructionList) {
            if (inst.getName().equalsIgnoreCase(name)) {
                if (matchingInstructions == null)
                    matchingInstructions = new ArrayList<>();
                matchingInstructions.add(inst);
            }
        }
        return matchingInstructions;
    }


    // TODO: check to see if autocomplete was accidentally removed

    /**
     * Given a string, will return the Instruction object(s) from the instruction
     * set whose operator mnemonic prefix matches it.  Case-insensitive.  For example
     * "s" will match "sw", "sh", "sb", etc.  Uses straight linear search technique.
     *
     * @param name a string
     * @return list of matching Instruction object(s), or null if none match.
     */
    public ArrayList<Instruction> prefixMatchOperator(String name) {
        ArrayList<Instruction> matchingInstructions = null;
        // Linear search for now....
        if (name != null) {
            for (Instruction inst : instructionList) {
                if (inst.getName().toLowerCase().startsWith(name.toLowerCase())) {
                    if (matchingInstructions == null)
                        matchingInstructions = new ArrayList<>();
                    matchingInstructions.add(inst);
                }
            }
        }
        return matchingInstructions;
    }

    /*
     * Method to find and invoke a syscall given its service number.  Each syscall
     * function is represented by an object in an array list.  Each object is of
     * a class that implements Syscall or extends AbstractSyscall.
     */

    public static void findAndSimulateSyscall(int number, ProgramStatement statement)
            throws SimulationException {
        AbstractSyscall service = SyscallLoader.findSyscall(number);
        if (service != null) {
            // TODO: find a cleaner way of doing this
            // This was introduced to solve issue #108
            boolean is_writing = service instanceof SyscallPrintChar ||
                    service instanceof SyscallPrintDouble ||
                    service instanceof SyscallPrintFloat ||
                    service instanceof SyscallPrintInt ||
                    service instanceof SyscallPrintIntBinary ||
                    service instanceof SyscallPrintIntHex ||
                    service instanceof SyscallPrintIntUnsigned ||
                    service instanceof SyscallPrintString ||
                    service instanceof SyscallWrite;
            if (!is_writing) {
                SystemIO.flush(true);
            }
            service.simulate(statement);
            return;
        }
        throw new SimulationException(statement,
                "invalid or unimplemented syscall service: " +
                        number + " ", SimulationException.ENVIRONMENT_CALL);
    }

    /*
     * Method to process a successful branch condition.  DO NOT USE WITH JUMP
     * INSTRUCTIONS!  The branch operand is a relative displacement in words
     * whereas the jump operand is an absolute address in bytes.
     *
     * The parameter is displacement operand from instruction.
     */

    public static void processBranch(int displacement) {
        // Decrement needed because PC has already been incremented
        RegisterFile.setProgramCounter(RegisterFile.getProgramCounter() + displacement - Instruction.INSTRUCTION_LENGTH);
    }

    /*
     * Method to process a jump.  DO NOT USE WITH BRANCH INSTRUCTIONS!
     * The branch operand is a relative displacement in words
     * whereas the jump operand is an absolute address in bytes.
     *
     * The parameter is jump target absolute byte address.
     */

    public static void processJump(int targetAddress) {
        RegisterFile.setProgramCounter(targetAddress);
    }

    /*
     * Method to process storing of a return address in the given
     * register.  This is used only by the "and link"
     * instructions: jal and jalr
     * The parameter is register number to receive the return address.
     */

    public static void processReturnAddress(int register) {
        RegisterFile.updateRegister(register, RegisterFile.getProgramCounter());
    }

    private static class MatchMap implements Comparable<MatchMap> {
        private int mask;
        private int maskLength; // number of 1 bits in mask
        private HashMap<Integer, BasicInstruction> matchMap;

        public MatchMap(int mask, HashMap<Integer, BasicInstruction> matchMap) {
            this.mask = mask;
            this.matchMap = matchMap;

            int k = 0;
            int n = mask;
            while (n != 0) {
                k++;
                n &= n - 1;
            }
            this.maskLength = k;
        }

        public boolean equals(Object o) {
            return o instanceof MatchMap && mask == ((MatchMap) o).mask;
        }

        public int compareTo(MatchMap other) {
            int d = other.maskLength - this.maskLength;
            if (d == 0) d = this.mask - other.mask;
            return d;
        }

        public BasicInstruction find(int instr) {
            int match = instr & mask;
            return matchMap.get(match);
        }
    }

    private void addBasicInstructions() {
        instructionList.add(new ADD());
        instructionList.add(new ADDI());
        instructionList.add(new AND());
        instructionList.add(new ANDI());
        instructionList.add(new AUIPC());
        instructionList.add(new BEQ());
        instructionList.add(new BGE());
        instructionList.add(new BGEU());
        instructionList.add(new BLT());
        instructionList.add(new BLTU());
        instructionList.add(new BNE());
        instructionList.add(new CSRRC());
        instructionList.add(new CSRRCI());
        instructionList.add(new CSRRS());
        instructionList.add(new CSRRSI());
        instructionList.add(new CSRRW());
        instructionList.add(new CSRRWI());
        instructionList.add(new DIV());
        instructionList.add(new DIVU());
        instructionList.add(new EBREAK());
        instructionList.add(new ECALL());
        instructionList.add(new FADDD());
        instructionList.add(new FADDS());
        instructionList.add(new FCLASSD());
        instructionList.add(new FCLASSS());
        instructionList.add(new FCVTDS());
        instructionList.add(new FCVTDW());
        instructionList.add(new FCVTDWU());
        instructionList.add(new FCVTSD());


        instructionList.add(new FCVTSW());
        instructionList.add(new FCVTSWU());
        instructionList.add(new FCVTWD());
        instructionList.add(new FCVTWS());
        instructionList.add(new FCVTWUD());
        instructionList.add(new FCVTWUS());
        instructionList.add(new FDIVD());
        instructionList.add(new FDIVS());
        instructionList.add(new FENCE());
        instructionList.add(new FENCEI());
        instructionList.add(new FEQD());
        instructionList.add(new FEQS());
        instructionList.add(new FLD());
        instructionList.add(new FLED());
        instructionList.add(new FLES());
        instructionList.add(new FLTD());
        instructionList.add(new FLTS());
        instructionList.add(new FLW());
        instructionList.add(new FMADDD());
        instructionList.add(new FMADDS());
        instructionList.add(new FMAXD());
        instructionList.add(new FMAXS());
        instructionList.add(new FMIND());
        instructionList.add(new FMINS());
        instructionList.add(new FMSUBD());
        instructionList.add(new FMSUBS());
        instructionList.add(new FMULD());
        instructionList.add(new FMULS());
        instructionList.add(new FMVSX());
        instructionList.add(new FMVXS());
        instructionList.add(new FNMADDD());
        instructionList.add(new FNMADDS());
        instructionList.add(new FNMSUBD());
        instructionList.add(new FNMSUBS());
        instructionList.add(new FSD());
        instructionList.add(new FSGNJD());
        instructionList.add(new FSGNJND());
        instructionList.add(new FSGNJNS());
        instructionList.add(new FSGNJS());
        instructionList.add(new FSGNJXD());
        instructionList.add(new FSGNJXS());
        instructionList.add(new FSQRTD());
        instructionList.add(new FSQRTS());
        instructionList.add(new FSUBD());
        instructionList.add(new FSUBS());
        instructionList.add(new FSW());
        instructionList.add(new JAL());
        instructionList.add(new JALR());
        instructionList.add(new LB());
        instructionList.add(new LBU());

        instructionList.add(new LH());
        instructionList.add(new LHU());
        instructionList.add(new LUI());
        instructionList.add(new LW());

        instructionList.add(new MUL());
        instructionList.add(new MULH());
        instructionList.add(new MULHSU());
        instructionList.add(new MULHU());
        instructionList.add(new OR());
        instructionList.add(new ORI());
        instructionList.add(new REM());
        instructionList.add(new REMU());

        instructionList.add(new SB());
        instructionList.add(new SH());
        instructionList.add(new SLL());
        instructionList.add(new SLLI());


        instructionList.add(new SLT());
        instructionList.add(new SLTI());
        instructionList.add(new SLTIU());
        instructionList.add(new SLTU());
        instructionList.add(new SRA());
        instructionList.add(new SRAI());

        instructionList.add(new SRL());
        instructionList.add(new SRLI());
        instructionList.add(new SUB());
        instructionList.add(new SW());
        instructionList.add(new URET());
        instructionList.add(new WFI());
        instructionList.add(new XOR());
        instructionList.add(new XORI());

        if (InstructionSet.rv64) {
            instructionList.add(new ADDW());
            instructionList.add(new ADDIW());
            instructionList.add(new DIVUW());
            instructionList.add(new DIVW());

            instructionList.add(new FCVTDL());
            instructionList.add(new FCVTDLU());
            instructionList.add(new FCVTLD());
            instructionList.add(new FCVTLS());
            instructionList.add(new FCVTLUD());
            instructionList.add(new FCVTLUS());
            instructionList.add(new FCVTSL());
            instructionList.add(new FCVTSLU());
            instructionList.add(new FMVDX());
            instructionList.add(new FMVXD());
            instructionList.add(new LD());
            instructionList.add(new LWU());
            instructionList.add(new MULW());
            instructionList.add(new REMUW());
            instructionList.add(new REMW());
            instructionList.add(new SD());
            instructionList.add(new SLLIW());
            instructionList.add(new SLLW());
            instructionList.add(new SRAIW());
            instructionList.add(new SRAW());
            instructionList.add(new SRLIW());
            instructionList.add(new SRLW());
            instructionList.add(new SUBW());


            instructionList.add(new SRAI64());
            instructionList.add(new SLLI64());
            instructionList.add(new SRLI64());
        }


    }
}

