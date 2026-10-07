package app.specy.rars.riscv.io;

/**
 * The IO environment failed to answer a syscall: a host handler threw or broke its contract. It
 * ends the run, which reports it as the host's failure rather than the program's.
 *
 * <p>Unlike {@link RISCVIOError}, which file services turn into a -1 result for the program, this
 * is never caught by a service.
 */
public class RISCVIOFailure extends RuntimeException {
    public RISCVIOFailure(String message) {
        super(message);
    }
}
