package app.specy.rars;

import app.specy.rars.riscv.hardware.AddressErrorException;

/**
 * Exceptions that cannot be handled and must result in the ending of the simulation
 * <p>
 * Used for exit syscalls and errors in syscalls
 */
public class ExitingException extends SimulationException {
    public ExitingException() {
        super();
    }

    public ExitingException(ProgramStatement statement, String message) {
        super(statement, message);
    }

    public ExitingException(ProgramStatement ps, AddressErrorException aee) {
        super(ps, aee);
    }

    public String toString(){
        return "Exit exception";
    }
}
