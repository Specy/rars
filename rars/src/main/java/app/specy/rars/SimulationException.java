package app.specy.rars;

import app.specy.rars.riscv.Instruction;
import app.specy.rars.riscv.hardware.AddressErrorException;
import app.specy.rars.riscv.hardware.RegisterFile;
import app.specy.rars.util.Binary;

/**
 * For exceptions thrown during runtime
 * <p>
 * if cause is -1, the exception is not-handlable is user code.
 */
public class SimulationException extends Exception {

    // Interrupts
    public static final int SOFTWARE_INTERRUPT = 0x80000000;
    public static final int TIMER_INTERRUPT = 0x80000004;
    public static final int EXTERNAL_INTERRUPT = 0x80000008;
    // Traps
    public static final int INSTRUCTION_ADDR_MISALIGNED = 0;
    public static final int INSTRUCTION_ACCESS_FAULT = 1;
    public static final int ILLEGAL_INSTRUCTION = 2;
    public static final int LOAD_ADDRESS_MISALIGNED = 4;
    public static final int LOAD_ACCESS_FAULT = 5;
    public static final int STORE_ADDRESS_MISALIGNED = 6;
    public static final int STORE_ACCESS_FAULT = 7;
    public static final int ENVIRONMENT_CALL = 8;

    private int cause = -1, value = 0;
    private ErrorMessage message = null;

    /**
     * What a runtime failure was, for the host that reports it: the program raising an exception
     * it had no handler for, a service it called refusing its arguments or its input, the host
     * failing to answer a service, or the simulator itself failing.
     */
    public enum Kind {
        /** A RISC-V exception the program had no handler for: a misaligned or faulting access, an illegal instruction. */
        EXCEPTION("exception"),
        /** A syscall the program made failed: an unknown service, invalid input, a bad argument. */
        SYSCALL("syscall"),
        /** The host failed to answer a service: its handler threw, rejected or broke its contract. */
        HANDLER("handler"),
        /** The simulator failed on its own. */
        INTERNAL("internal");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        /** The kind as the host API spells it. */
        public String label() {
            return label;
        }
    }

    private Kind kind = Kind.EXCEPTION;
    /** The address of the instruction that failed, or of the program counter that could not be fetched. */
    private int address;

    public SimulationException() {
    }

    private SimulationException(ErrorMessage message, Throwable cause) {
        super(message.toString(), cause);
        this.message = message;
    }

    /**
     * A failure that was not the program's own doing, while it executed {@code statement} at
     * {@code address}: the host failing to answer a service ({@link Kind#HANDLER}) or the
     * simulator failing ({@link Kind#INTERNAL}). It ends the program like an exception it has no
     * handler for, but never reaches the program's own trap handler.
     *
     * @param statement the statement executing, or null when there was none
     * @param cause     what failed
     */
    public static SimulationException duringExecution(ProgramStatement statement, int address, Kind kind,
            Throwable cause) {
        String text = cause.getMessage();
        if (text == null || text.isEmpty()) {
            text = cause.toString();
        }
        if (kind == Kind.INTERNAL) {
            text = "Internal error: " + text;
        }
        ErrorMessage message = statement == null ? new ErrorMessage((RISCVprogram) null, 0, 0, text)
                : new ErrorMessage(statement, text);
        SimulationException failure = new SimulationException(message, cause);
        failure.kind = kind;
        failure.address = address;
        return failure;
    }

    /** What the runtime failure was; {@link Kind#EXCEPTION} unless something said otherwise. */
    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    /**
     * The address of the instruction that failed, or, when the program counter itself could not be
     * fetched, that program counter. The simulator sets it when a failure ends a run.
     */
    public int getAddress() {
        return address;
    }

    public void setAddress(int address) {
        this.address = address;
    }

    public SimulationException(ProgramStatement ps, String m, int cause) {
        this(ps, m);
        this.cause = cause;
    }

    /**
     * Constructor for ProcessingException to handle runtime exceptions
     *
     * @param ps a ProgramStatement of statement causing runtime exception
     * @param m  a String containing specialized error message
     **/
    public SimulationException(ProgramStatement ps, String m) {
        super(new ErrorMessage(ps, "Runtime exception at " +
                Binary.intToHexString(RegisterFile.getProgramCounter() - Instruction.INSTRUCTION_LENGTH) +
                ": " + m).toString());
        message = new ErrorMessage(ps, "Runtime exception at " +
                Binary.intToHexString(RegisterFile.getProgramCounter() - Instruction.INSTRUCTION_LENGTH) +
                ": " + m);
        // Stopped using ps.getAddress() because of pseudo-instructions.  All instructions in
        // the macro expansion point to the same ProgramStatement, and thus all will return the
        // same value for getAddress(). But only the first such expanded instruction will
        // be stored at that address.  So now I use the program counter (which has already
        // been incremented).
    }

    public SimulationException(ProgramStatement ps, AddressErrorException aee) {
        this(ps, aee.getMessage());
        cause = aee.getType();
        value = aee.getAddress();
    }

    public SimulationException(String m) {
        super(m);
        message = new ErrorMessage(null, 0, 0, m);
    }

    public SimulationException(String m, int cause) {
        super(m);
        message = new ErrorMessage(null, 0, 0, m);
        this.cause = cause;
    }

    /**
     * Produce the list of error messages.
     *
     * @return Returns the Message associated with the exception
     * @see ErrorMessage
     **/
    public ErrorMessage error() {
        return message;
    }

    public int cause() {
        return cause;
    }

    public int value() {
        return value;
    }
}
