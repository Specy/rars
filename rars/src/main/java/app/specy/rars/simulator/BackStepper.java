package app.specy.rars.simulator;

import app.specy.rars.Globals;
import app.specy.rars.ProgramStatement;
import app.specy.rars.riscv.Instruction;
import app.specy.rars.riscv.hardware.AddressErrorException;
import app.specy.rars.riscv.hardware.ControlAndStatusRegisterFile;
import app.specy.rars.riscv.hardware.FloatingPointRegisterFile;
import app.specy.rars.riscv.hardware.Memory;
import app.specy.rars.riscv.hardware.RegisterFile;
import app.specy.rars.Settings;

import java.util.ArrayList;
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
 * Used to "step backward" through execution, undoing each instruction.
 *
 * @author Pete Sanderson
 * @version February 2006
 */

public class BackStepper {
    private enum Action {
        MEMORY_RESTORE_RAW_WORD,
        MEMORY_RESTORE_DOUBLE_WORD,
        MEMORY_RESTORE_WORD,
        MEMORY_RESTORE_HALF,
        MEMORY_RESTORE_BYTE,
        REGISTER_RESTORE,
        PC_RESTORE,
        CONTROL_AND_STATUS_REGISTER_RESTORE,
        CONTROL_AND_STATUS_REGISTER_BACKDOOR,
        FLOATING_POINT_REGISTER_RESTORE,
        DO_NOTHING,
        CONTROL_AND_STATUS_COUNTERS_DECREMENT,
        /**
         * Restores one control and status register through the register's own setValue. It exists
         * because neither of the two restores above is the inverse of the host's CSR setter:
         * CONTROL_AND_STATUS_REGISTER_RESTORE refuses a read only register, so it could never put
         * `cycle` back, and CONTROL_AND_STATUS_REGISTER_BACKDOOR writes the register's own field,
         * so it would leave a linked register (`fflags`, `cycleh`) holding a value while the base
         * it aliases kept the poked one. Only a poke records this action.
         */
        CONTROL_AND_STATUS_REGISTER_POKE_RESTORE,
        /**
         * One whole poke, however many values it wrote: the entry carries its restores itself
         * rather than taking a stack slot per value, so that a poke costs one slot of the history
         * capacity and can never be evicted in part.
         */
        POKE
    }

    // Flag to mark BackStep object as prepresenting specific situation: user manipulates
    // memory/register value via GUI after assembling program but before running it.
    private static final int NOT_PC_VALUE = -1;

    // Memo for the statement lookup every push makes; see BackStep.assign.
    private int lastStatementPc = NOT_PC_VALUE;
    private ProgramStatement lastStatement;


    private boolean engaged;
    private final BackstepStack backSteps;

    /*
     * A poke - a register or memory value written by the host between two instructions - is one
     * entry of this same history, undone by one backStep() like an instruction, and one slot of its
     * capacity however many values it wrote. While a transaction is open the restores the
     * simulator's own write paths record are collected in pokeRestores instead of being pushed, and
     * endPoke() pushes them as a single POKE entry; backStep() then applies them in reverse.
     * Pushing one slot per value would make a long memory poke evict the instructions before it
     * and, worse, let the poke be half evicted: undoable only in part while the entry reporting it
     * still claimed to hold every write.
     *
     * The entry carries a group key of its own, negative and distinct per transaction, so that it
     * is never an instruction address - a poke entry also keeps pc == NOT_PC_VALUE, so backStep()
     * restores no program counter for it - and so that the finished writes a caller remembers can
     * be matched to the entry that still holds them.
     *
     * The counter is static so that a key is never reused within a session: reassembling builds a
     * fresh BackStepper, and a per-instance counter would hand the new stack keys that a caller may
     * still be holding poke records under.
     */
    private static final int NO_POKE_GROUP = 0;
    private static int nextPokeGroup = -2;
    private int pokeGroup = NO_POKE_GROUP;
    /** The open transaction's restores, oldest first. */
    private List<PokeRestore> pokeRestores;

    /** One value a poke overwrote, and how to put it back. */
    private static final class PokeRestore {
        private final Action action;
        private final int param1;
        private final long param2;

        private PokeRestore(Action action, int param1, long param2) {
            this.action = action;
            this.param1 = param1;
            this.param2 = param2;
        }
    }

    // One can argue using java.util.Stack, given its clumsy implementation.
    // A homegrown linked implementation will be more streamlined, but
    // I anticipate that backstepping will only be used during timed
    // (currently max 30 instructions/second) or stepped execution, where
    // performance is not an issue.  Its Vector implementation may result
    // in quicker garbage collection than a pure linked list implementation.

    /**
     * Create a fresh BackStepper.  It is enabled, which means all
     * subsequent instruction executions will have their "undo" action
     * recorded here.
     */
    public BackStepper() {
        engaged = true;
        backSteps = new BackstepStack(Globals.maximumBacksteps);
    }

    public BackstepStack getBackStepsStack() {
        return backSteps;
    }


    /**
     * Determine whether execution "undo" steps are currently being recorded.
     *
     * @return true if undo steps being recorded, false if not.
     */
    public boolean enabled() {
        return engaged;
    }

    /**
     * Set enable status.
     *
     * @param state If true, will begin (or continue) recoding "undo" steps.  If false, will stop.
     */
    public void setEnabled(boolean state) {
        engaged = state;
    }

    /**
     * Test whether there are steps that can be undone.
     *
     * @return true if there are no steps to be undone, false otherwise.
     */
    public boolean empty() {
        return backSteps.empty();
    }

    /**
     * Carry out a "back step", which will undo the latest execution step.
     * Does nothing if backstepping not enabled or if there are no steps to undo.
     */

    // Note that there may be more than one "step" in an instruction execution; for
    // instance the multiply, divide, and double-precision floating point operations
    // all store their result in register pairs which results in two store operations.
    // Both must be undone transparently, so we need to detect that multiple steps happen
    // together and carry out all of them here.
    // Use a do-while loop based on the backstep's program statement reference.
    public void backStep() {
        if (engaged && !backSteps.empty()) {
            BackStep first = backSteps.peek();
            engaged = false; // GOTTA DO THIS SO METHOD CALL IN SWITCH WILL NOT RESULT IN NEW ACTION ON STACK!
            do {
                BackStep step = backSteps.pop();
            /*
                System.out.println("backstep POP: action "+step.action+" pc "+rars.util.Binary.intToHexString(step.pc)+
            	                   " source "+((step.ps==null)? "none":step.ps.getSource())+
            							 " parm1 "+step.param1+" parm2 "+step.param2);
            */
                if (step.pc != NOT_PC_VALUE) {
                    RegisterFile.setProgramCounter(step.pc);
                }
                try {
                    if (step.action == Action.POKE) {
                        // Newest write first, so that a value the poke wrote twice comes back to
                        // what it held before the first of those writes.
                        for (int i = step.pokeRestores.length - 1; i >= 0; i--) {
                            PokeRestore restore = step.pokeRestores[i];
                            applyRestore(restore.action, restore.param1, restore.param2);
                        }
                    } else {
                        applyRestore(step.action, step.param1, step.param2);
                    }
                } catch (Exception e) {
                    // if the original action did not cause an exception this will not either.
                    System.out.println("Internal RARS error: address exception while back-stepping.");
                    throw new RuntimeException();
                }
            } while (!backSteps.empty() && sameGroup(first, backSteps.peek()));
            engaged = true;  // RESET IT (was disabled at top of loop -- see comment)
        }
    }

    /** Carries out one recorded restore. One back step holds one; a poke entry holds its writes. */
    private static void applyRestore(Action action, int param1, long param2) throws AddressErrorException {
        switch (action) {
            case MEMORY_RESTORE_RAW_WORD:
                Globals.memory.setRawWord(param1, (int) param2);
                break;
            case MEMORY_RESTORE_DOUBLE_WORD:
                Globals.memory.setDoubleWord(param1, param2);
                break;
            case MEMORY_RESTORE_WORD:
                Globals.memory.setWord(param1, (int) param2);
                break;
            case MEMORY_RESTORE_HALF:
                Globals.memory.setHalf(param1, (int) param2);
                break;
            case MEMORY_RESTORE_BYTE:
                Globals.memory.setByte(param1, (int) param2);
                break;
            case REGISTER_RESTORE:
                RegisterFile.updateRegister(param1, param2);
                break;
            case FLOATING_POINT_REGISTER_RESTORE:
                FloatingPointRegisterFile.updateRegisterLong(param1, param2);
                break;
            case CONTROL_AND_STATUS_REGISTER_RESTORE:
                ControlAndStatusRegisterFile.updateRegister(param1, param2);
                break;
            case CONTROL_AND_STATUS_REGISTER_BACKDOOR:
                ControlAndStatusRegisterFile.updateRegisterBackdoor(param1, param2);
                break;
            case CONTROL_AND_STATUS_REGISTER_POKE_RESTORE:
                ControlAndStatusRegisterFile.updateRegisterDirectly(param1, param2);
                break;
            case PC_RESTORE:
                RegisterFile.setProgramCounter(param1);
                break;
            case CONTROL_AND_STATUS_COUNTERS_DECREMENT:
                ControlAndStatusRegisterFile.decrementCounters();
                break;
            case DO_NOTHING:
                break;
            case POKE:
                break; // handled by the caller, which holds the writes
        }
    }

    /**
     * Whether two back steps are undone together by one {@link #backStep()} call, which is what
     * makes them one entry of the history. Instruction steps are grouped by the statement they
     * belong to, exactly as backStep() has always grouped them - two host writes made before
     * anything ran share the null statement and so still group together. A poke is a single back
     * step holding all of its writes, so it groups with nothing: not with the instruction below it,
     * and not with the poke before it.
     *
     * @param one   a back step.
     * @param other the back step below it on the stack.
     * @return true if undoing one also undoes the other.
     */
    public static boolean sameGroup(BackStep one, BackStep other) {
        if (one.isPoke() || other.isPoke()) {
            return false;
        }
        return one.ps == other.ps;
    }

    /**
     * Open a poke transaction: every restore recorded until {@link #endPoke()} is collected rather
     * than pushed, and endPoke() pushes the lot as one back step, which one backStep() undoes as a
     * unit and which costs one slot of the history. No instruction may execute while it is open.
     *
     * @return the group key given to this transaction.
     * @throws IllegalStateException if a poke is already open.
     */
    public int beginPoke() {
        if (pokeGroup != NO_POKE_GROUP) {
            throw new IllegalStateException("A poke is already open");
        }
        if (nextPokeGroup >= NOT_PC_VALUE) { // wrapped past the negative side; start over
            nextPokeGroup = -2;
        }
        pokeGroup = nextPokeGroup--;
        pokeRestores = new ArrayList<>();
        return pokeGroup;
    }

    /**
     * Close the open poke transaction.
     *
     * @return true if it pushed the one back step the poke became; false if it had nothing to
     * record, either because nothing was written or because recording is disabled, in which case
     * the writes stand but cannot be undone.
     * @throws IllegalStateException if no poke is open.
     */
    public boolean endPoke() {
        if (pokeGroup == NO_POKE_GROUP) {
            throw new IllegalStateException("No poke is open");
        }
        int group = pokeGroup;
        List<PokeRestore> restores = pokeRestores;
        // Cleared before the push, so that the push itself goes on the stack rather than back into
        // the transaction it is closing.
        pokeGroup = NO_POKE_GROUP;
        pokeRestores = null;
        if (restores.isEmpty()) {
            return false;
        }
        backSteps.pushPoke(group, restores.toArray(new PokeRestore[0]));
        return true;
    }

    /**
     * Whether a poke transaction is open, so that the host's setters journal into it rather than
     * writing straight through.
     */
    public boolean pokeOpen() {
        return pokeGroup != NO_POKE_GROUP;
    }

    /**
     * Record, into the open poke, that one control and status register held {@code value} before
     * the host overwrote it. The host's CSR setter writes through the register's own setValue, a
     * path no register file routes through the back stepper, so it says so itself.
     *
     * @param number The architectural CSR number, not a position in the file.
     * @param value  The value the register held before the write.
     * @throws IllegalStateException if no poke is open.
     */
    public void addPokeControlAndStatusRestore(int number, long value) {
        if (pokeGroup == NO_POKE_GROUP) {
            throw new IllegalStateException("No poke is open");
        }
        if (!engaged) {
            return; // recording is off: the write stands, as every other write in this poke does
        }
        // The program counter is not read: push collects this into the open transaction, and a
        // poke entry belongs to no instruction.
        backSteps.push(Action.CONTROL_AND_STATUS_REGISTER_POKE_RESTORE, NOT_PC_VALUE, number, value);
    }
  
     
      /* Convenience method called below to get program counter value.  If it needs to be
        * be modified (e.g. to subtract 4) that can be done here in one place.
   	 */

    private int pc() {
        // PC incremented prior to instruction simulation, so need to adjust for that.
        return RegisterFile.getProgramCounter() - Instruction.INSTRUCTION_LENGTH;
    }

    /**
     * Add a new "back step" (the undo action) to the stack. The action here
     * is to restore a raw memory word value (setRawWord).
     *
     * @param address  The affected memory address.
     * @param value    The "restore" value to be stored there.
     * @param newValue The word the write left at that address.
     * @return the argument value
     */
    public int addMemoryRestoreRawWord(int address, int value, int newValue) {
        backSteps.push(Action.MEMORY_RESTORE_RAW_WORD, pc(), address, value, newValue, 0);
        return value;
    }

    /**
     * Add a new "back step" (the undo action) to the stack. The action here
     * is to restore a memory word value.
     *
     * @param address  The affected memory address.
     * @param value    The "restore" value to be stored there.
     * @param newValue The word the write left at that address.
     * @return the argument value
     */
    public int addMemoryRestoreWord(int address, int value, int newValue) {
        backSteps.push(Action.MEMORY_RESTORE_WORD, pc(), address, value, newValue, 0);
        return value;
    }

    /**
     * @param address  The affected memory address.
     * @param value    The "restore" value to be stored there.
     * @param newValue The double word the write left at that address.
     * @return the argument value
     */
    public long addMemoryRestoreDoubleWord(int address, long value, long newValue) {
        backSteps.push(Action.MEMORY_RESTORE_DOUBLE_WORD, pc(), address, value, 0, newValue);
        return value;
    }

    /**
     * Add a new "back step" (the undo action) to the stack.  The action here
     * is to restore a memory half-word value.
     *
     * @param address  The affected memory address.
     * @param value    The "restore" value to be stored there, in low order half.
     * @param newValue The half the write left at that address, in low order half.
     * @return the argument value
     */
    public int addMemoryRestoreHalf(int address, int value, int newValue) {
        backSteps.push(Action.MEMORY_RESTORE_HALF, pc(), address, value, newValue, 0);
        return value;
    }

    /**
     * Add a new "back step" (the undo action) to the stack.  The action here
     * is to restore a memory byte value.
     *
     * @param address  The affected memory address.
     * @param value    The "restore" value to be stored there, in low order byte.
     * @param newValue The byte the write left at that address, in low order byte.
     * @return the argument value
     */
    public int addMemoryRestoreByte(int address, int value, int newValue) {
        backSteps.push(Action.MEMORY_RESTORE_BYTE, pc(), address, value, newValue, 0);
        return value;
    }

    /**
     * Add a new "back step" (the undo action) to the stack.  The action here
     * is to restore a register file register value.
     *
     * @param register The affected register number.
     * @param value    The "restore" value to be stored there.
     * @param newValue The whole register's value after the write.
     * @return the argument value
     */
    public long addRegisterFileRestore(int register, long value, long newValue) {
        backSteps.push(Action.REGISTER_RESTORE, pc(), register, value, 0, newValue);
        return value;
    }

    /**
     * Add a new "back step" (the undo action) to the stack.  The action here
     * is to restore the program counter.
     *
     * @param value    The "restore" value to be stored there.
     * @param newValue The address the instruction set the program counter to, unadjusted: the
     *                 branch or jump target it just took.
     * @return the argument value
     */
    public int addPCRestore(int value, int newValue) {
        // adjust for value reflecting incremented PC.
        value -= Instruction.INSTRUCTION_LENGTH;
        // Use "value" insead of "pc()" for second arg because RegisterFile.getProgramCounter()
        // returns branch target address at this point.
        // param2 is unused by this action and stays 0, as it always has been: the address put back
        // is param1, and the written value is the address the instruction set.
        backSteps.push(Action.PC_RESTORE, value, value, 0, newValue, 0);
        return value;
    }

    /**
     * Add a new "back step" (the undo action) to the stack.  The action here
     * is to restore a control and status register value.
     *
     * @param register The affected register number.
     * @param value    The "restore" value to be stored there.
     * @param newValue The whole register's value after the write, read as the old value is: a
     *                 masked register keeps the bits it does not own and a linked register reports
     *                 its own field rather than the register it aliases.
     * @return the argument value
     */
    public long addControlAndStatusRestore(int register, long value, long newValue) {
        backSteps.push(Action.CONTROL_AND_STATUS_REGISTER_RESTORE, pc(), register, value, 0, newValue);
        return value;
    }


    /**
     * Add a new "back step" (the undo action) to the stack.  The action here
     * is to restore a control and status register value. This does not obey
     * read only restrictions and does not notify observers.
     *
     * Its only writer is the simulator sampling the wall clock into the `time` counter, rather
     * than a program writing a register, but it is a write that lands in an instruction's entry all
     * the same, so it reports both sides like any other: `setValueBackdoor` stores the value it is
     * given verbatim, whatever kind of register it is, so the written value is the argument itself.
     *
     * @param register The affected register number.
     * @param value    The "restore" value to be stored there.
     * @param newValue The value the backdoor write left in the register.
     * @return the argument value
     */
    public long addControlAndStatusBackdoor(int register, long value, long newValue) {
        backSteps.push(Action.CONTROL_AND_STATUS_REGISTER_BACKDOOR, pc(), register, value, 0, newValue);
        return value;
    }

    /**
     * Add a new "back step" (the undo action) to the stack.  The action here is to undo one
     * instruction's worth of the cycle and instret counters.
     *
     * The entry carries no values because it does not need them. Both counters are
     * {@link app.specy.rars.riscv.hardware.ReadOnlyRegister}s, which
     * {@code ControlAndStatusRegisterFile.updateRegister} refuses to write, so no program can
     * assign them; the only code that writes them is the simulator loop, and it only ever adds one
     * to each, once per instruction. Undoing that is a subtraction, so one entry replaces the two
     * this used to push. A backStep that pops several iterations of a self-branching instruction at
     * once is still exact, because each iteration left its own entry and so its own subtraction.
     *
     * `RISCVEmulator.test.ts` holds that invariant: if the counters ever gain a second writer, or
     * stop moving in step with the instruction count, those tests fail rather than undo silently
     * restoring the wrong values.
     *
     * @param programCounter The address of the instruction that was counted. This is passed in
     *                       rather than read from `pc()`, which subtracts one instruction from the
     *                       program counter and so names the wrong address once the instruction has
     *                       branched: a jump back to the entry point made it name the word below
     *                       the text segment, leaving the entry with no statement to group under.
     */
    public void addControlAndStatusCountersDecrement(int programCounter) {
        backSteps.push(Action.CONTROL_AND_STATUS_COUNTERS_DECREMENT, programCounter);
    }

    /**
     * Add a new "back step" (the undo action) to the stack.  The action here
     * is to restore a floating point register value.
     *
     * @param register The affected register number.
     * @param value    The "restore" value to be stored there.
     * @param newValue The whole register's value after the write, NaN boxing and all.
     * @return the argument value
     */
    public long addFloatingPointRestore(int register, long value, long newValue) {
        backSteps.push(Action.FLOATING_POINT_REGISTER_RESTORE, pc(), register, value, 0, newValue);
        return value;
    }

    /**
     * Add a new "back step" (the undo action) to the stack.  The action here
     * is to do nothing!  This is just a place holder so when user is backstepping
     * through the program no instructions will be skipped.  Cosmetic. If the top of the
     * stack has the same PC counter, the do-nothing action will not be added.
     */
    public void addDoNothing(int pc) {
        if (backSteps.empty() || backSteps.peek().pc != pc) {
            backSteps.push(Action.DO_NOTHING, pc);
        }
    }


    // Represents a "back step" (undo action) on the stack.
    public class BackStep {
        private Action action;  // what do do MEMORY_RESTORE_WORD, etc
        private int pc;      // program counter value when original step occurred
        private ProgramStatement ps;   // statement whose action is being "undone" here
        private int param1;  // first parameter required by that action
        private long param2;  // optional second parameter required by that action
        /*
         * The value the write left behind, beside the one param2 puts back: the whole register
         * after a register, floating point or CSR write, the bytes at the address after a memory
         * write at the width it was made, and the address the instruction set after a PC restore.
         * An action that restores no value of its own - the counters decrement, DO_NOTHING and a
         * poke, whose own writes report both sides themselves - reports 0 and writes neither field.
         *
         * Recording it at all is the whole cost of this change, and the shape here is what makes
         * that cost nothing. Measured on the `addi`/`j` compute loop the editor's harness uses,
         * against both the published 3.5.0 build and a build of the release commit made here:
         *
         *  - Two fields rather than one, so that each write path stores the value in the width it
         *    already holds. TeaVM compiles a Java long into an object of its own (the generated
         *    code calls Long_fromInt to make one), so a single long field made every jump allocate
         *    a Long for its target: about 2% of the loop.
         *  - One push body. Giving the actions that carry a value a push of their own cost 6%, and
         *    recording the value through a second little method called after each push cost 7%,
         *    both without storing anything more than this does. push() is the bookkeeping every
         *    simulated instruction pays, and it measures as the release build only while it stays
         *    one body that the compiler keeps inlining into the write paths.
         *  - `advance`, called from that body, wraps with a comparison rather than a remainder,
         *    which pays for the two stores.
         *
         * Only the field belonging to the entry's action is read, so a recycled slot needs no
         * clearing: getNewValue() picks the field by action, and returns 0 where there is no value.
         */
        private int param3Int;
        private long param3Long;
        private int pokeGroup; // the poke transaction this step is, or 0 for an instruction's step
        // A poke's restores, oldest first. They live in the entry rather than in a slot each, so
        // that a poke of a hundred bytes is still one slot of the history and is never evicted in
        // part. Null for an instruction's step.
        private PokeRestore[] pokeRestores;

        /**
         * Whether this step is a whole poke - every value the host wrote in one transaction -
         * rather than one effect of an instruction.
         */
        public boolean isPoke() {
            return pokeGroup != NO_POKE_GROUP;
        }

        /**
         * The key of the poke this step is, or 0 for an instruction's step. Two consecutive pokes
         * have different keys, and no key is ever an instruction address.
         */
        public int getPokeGroup() {
            return pokeGroup;
        }

        // it is critical that BackStep object get its values by calling this method
        // rather than assigning to individual members, because of the technique used
        // to set its ps member (and possibly pc).
        private void assign(Action act, int programCounter, int parm1, long parm2) {
            action = act;
            pc = programCounter;
            // Stack entries are recycled, so never inherit the last poke that used this slot.
            pokeGroup = NO_POKE_GROUP;
            pokeRestores = null;
            // Client does not have direct access to program statement, and rather than making all
            // of them go through the methods below to obtain it, we will do it here.
            // Want the program statement but do not want observers notified.
            //
            // The lookup is guarded rather than wrapped in a bare catch: `pc()` is the program
            // counter minus one instruction, so a branch taken to the first instruction of the text
            // segment asks for the word below it, and every push on such a step used to build and
            // throw an AddressErrorException. Filling in that exception's stack trace and its
            // formatted message dominated simulation of any loop whose target is the entry point.
            //
            // The result is memoised on the program counter it was read for, because an instruction
            // pushes more than one entry and every one of them asks for the same statement. Only
            // the grouping in `backStep` reads `ps`, so a program that rewrote the instruction at
            // this address between two pushes would change how its undo entries group, not what any
            // of them restores.
            ProgramStatement statement;
            if (programCounter == lastStatementPc) {
                statement = lastStatement;
            } else {
                statement = null;
                if (Memory.wordAligned(programCounter) && (Memory.inTextSegment(programCounter)
                        || Globals.getSettings().getBooleanSetting(Settings.Bool.SELF_MODIFYING_CODE_ENABLED))) {
                    try {
                        statement = Globals.memory.getStatementNoNotify(programCounter);
                    } catch (Exception e) {
                        statement = null;
                    }
                }
                lastStatementPc = programCounter;
                lastStatement = statement;
            }
            if (statement == null) {
                // The action will not be associated with any instruction, but will be carried out
                // when popped.
                pc = NOT_PC_VALUE; // Backstep method above will see this as flag to not set PC
            }
            ps = statement;
            param1 = parm1;
            param2 = parm2;
         /*				
            System.out.println("backstep PUSH: action "+action+" pc "+rars.util.Binary.intToHexString(pc)+
         		                   " source "+((ps==null)? "none":ps.getSource())+
         								 " parm1 "+param1+" parm2 "+param2);
         */
        }

        // A poke belongs to no instruction: it keeps NOT_PC_VALUE so that undoing it leaves the
        // program counter alone, and carries its transaction key instead, which tells it apart from
        // the poke before it. Its restores travel with it, which is what makes the whole
        // transaction one slot of the stack.
        private void assignPoke(int group, PokeRestore[] restores) {
            action = Action.POKE;
            pc = NOT_PC_VALUE;
            ps = null;
            param1 = 0;
            param2 = 0;
            param3Int = 0;
            param3Long = 0;
            pokeGroup = group;
            pokeRestores = restores;
        }

        public int getAction() {
            if(action == null) return -1;
            return action.ordinal();
        }

        public int getPc() {
            return pc;
        }

        public int getParam1() {
            return param1;
        }

        public long getParam2() {
            return param2;
        }

        /**
         * The value this step replaced, whole and untruncated: {@link #getParam2()} for every
         * action that carries one, except PC_RESTORE, which has always kept the address it puts
         * back in param1 and left param2 at 0.
         */
        public long getOldValue() {
            return action == Action.PC_RESTORE ? param1 : param2;
        }

        /**
         * The value the write left, beside {@link #getOldValue()}, as the simulator saw it at the
         * write: the whole register after a register, floating point or CSR write, the bytes at the
         * address after a memory write at the width it was made, and the address the instruction
         * set after a PC restore. 0 for an action that wrote no value of its own.
         *
         * The two fields are one value in two widths; see them for why the store is split.
         */
        public long getNewValue() {
            switch (action) {
                case REGISTER_RESTORE:
                case FLOATING_POINT_REGISTER_RESTORE:
                case CONTROL_AND_STATUS_REGISTER_RESTORE:
                case CONTROL_AND_STATUS_REGISTER_BACKDOOR:
                case MEMORY_RESTORE_DOUBLE_WORD:
                    return param3Long;
                case MEMORY_RESTORE_RAW_WORD:
                case MEMORY_RESTORE_WORD:
                case MEMORY_RESTORE_HALF:
                case MEMORY_RESTORE_BYTE:
                case PC_RESTORE:
                    return param3Int;
                default:
                    return 0;
            }
        }
    }

    // *****************************************************************************
    // special purpose stack class for backstepping.  You've heard of circular queues
    // implemented with an array, right?  This is a circular stack!  When full, the
    // newly-pushed item overwrites the oldest item, with circular top!  All operations
    // are constant time.  Upstream synchronized it too, to be safe (it was used by both the
    // simulation thread and the GUI thread for the back-step button); this fork is headless and
    // single threaded under TeaVM, and the stack is pushed twice per instruction, so the monitors
    // cost more than the operations they guard.
    // Upon construction, it is filled with newly-created empty BackStep objects which
    // will exist for the life of the stack.  Push does not create a BackStep object
    // but instead overwrites the contents of the existing one.  Thus during RISCV
    // program (simulated) execution, BackStep objects are never created or junked
    // regardless of how many steps are executed.  This will speed things up a bit
    // and make life easier for the garbage collector.

    public class BackstepStack {
        private final int capacity;
        private int size;
        private int top;
        private final BackStep[] stack;

        // Stack is created upon successful assembly or reset.  The one-time overhead of
        // creating all the BackStep objects will not be noticed by the user, and enhances
        // runtime performance by not having to create or recycle them during
        // program execution.
        private BackstepStack(int capacity) {
            this.capacity = capacity;
            this.size = 0;
            this.top = -1;
            this.stack = new BackStep[capacity];
            for (int i = 0; i < capacity; i++) {
                this.stack[i] = new BackStep();
            }
        }

        public BackStep[] getStack() {
            //get only the used part of the stack
            BackStep[] usedStack = new BackStep[size];
            for (int i = 0; i < size; i++) {
                usedStack[i] = stack[(top - i + capacity) % capacity];
            }
            return usedStack;
        }

        private boolean empty() {
            return size == 0;
        }

        // Moves the top onto the slot the next entry is written into, dropping the oldest entry
        // once the stack is full.
        private void advance() {
            // The wrap is a comparison rather than a remainder. `capacity` is not a constant, so
            // `%` is a real division in the generated JavaScript, and this runs two or three times
            // per simulated instruction - it is what pays for the written value each push now
            // stores. The walk is the same as before: the top moves up one and wraps at the
            // capacity, and the size stops growing once the stack is full, from which point the
            // entry the top lands on is the oldest one, overwritten as it always was.
            int next = top + 1;
            top = (next == capacity) ? 0 : next;
            if (size < capacity) {
                size++;
            }
        }

        /**
         * The one push: every entry of the stack is written here, so this is the whole of the
         * bookkeeping a simulated instruction pays for its history and it is kept to one body. The
         * value the write left travels in the width the caller already holds it in - `wroteInt` for
         * the sized memory writes and the program counter, `wroteLong` for a register, floating
         * point or CSR write and for an `sd` - because TeaVM compiles a Java long into an object
         * and widening an int into one allocates, which a jump would pay on every iteration of a
         * loop. An action with no written value of its own passes zero for both; see BackStep's
         * param3 fields for which one each action reads back.
         */
        private void push(Action act, int programCounter, int parm1, long parm2, int wroteInt, long wroteLong) {
            // While a poke is open no instruction is running, so every recorded restore is one of
            // its writes: it is collected rather than pushed, and the whole transaction is pushed
            // as one entry by endPoke(), whichever write path each of them came through. The
            // written value is dropped here on purpose: a poke reports both sides of every value it
            // wrote through its own writes, read at endPoke().
            if (pokeGroup != NO_POKE_GROUP) {
                pokeRestores.add(new PokeRestore(act, parm1, parm2));
                return;
            }
            advance();
            // We'll re-use existing objects rather than create/discard each time.
            // Must use assign() method rather than series of assignment statements!
            BackStep step = stack[top];
            step.assign(act, programCounter, parm1, parm2);
            step.param3Int = wroteInt;
            step.param3Long = wroteLong;
        }

        // For an action that records no written value of its own.
        private void push(Action act, int programCounter, int parm1, long parm2) {
            push(act, programCounter, parm1, parm2, 0, 0);
        }

        // The one entry a finished poke becomes. It is pushed by endPoke(), after the transaction
        // has been closed, so it takes the ordinary slot an instruction's step would.
        private void pushPoke(int group, PokeRestore[] restores) {
            advance();
            stack[top].assignPoke(group, restores);
        }

        private void push(Action act, int programCounter, int parm1) {
            push(act, programCounter, parm1, 0);
        }

        private void push(Action act, int programCounter) {
            push(act, programCounter, 0, 0);
        }

        // NO PROTECTION.  This class is used only within this file so there is no excuse
        // for trying to pop from empty stack.
        private BackStep pop() {
            BackStep bs;
            bs = stack[top];
            if (size == 1) {
                top = -1;
            } else {
                top = (top + capacity - 1) % capacity;
            }
            size--;
            return bs;
        }

        // NO PROTECTION.  This class is used only within this file so there is no excuse
        // for trying to peek from empty stack.
        private BackStep peek() {
            return stack[top];
        }

    }

}