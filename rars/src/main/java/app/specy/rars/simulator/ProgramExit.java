package app.specy.rars.simulator;

import app.specy.rars.Globals;

/**
 * Whether the program has exited, and with which code: state of the run like a register, which
 * {@code initialize} resets and undo puts back. The exit services set it (exit with code 0, exit2
 * with its operand). Running off the end of the program is not an exit: it leaves the code at 0
 * and is told by the program counter, which has no statement to run.
 *
 * <p>The code lives in {@link Globals#exitCode}, where RARS keeps it.
 */
public final class ProgramExit {
    private static boolean exited;

    private ProgramExit() {
    }

    /** Whether an exit service has run since the program was initialized, and not been undone. */
    public static boolean hasExited() {
        return exited;
    }

    /** The exit code: exit2's operand once it has run, 0 otherwise. */
    public static int code() {
        return Globals.exitCode;
    }

    /**
     * An exit service ran with {@code code}. It is recorded in the history like a register write, so
     * that undoing the exit puts the program back on the ecall, still running.
     */
    public static void exit(int code) {
        if (Globals.getSettings().getBackSteppingEnabled()) {
            Globals.program.getBackStepper().addExitRestore(exited, Globals.exitCode, code);
        }
        exited = true;
        Globals.exitCode = code;
    }

    /** Undo of an exit: what the history recorded before it. */
    static void restore(boolean wasExited, int previousCode) {
        exited = wasExited;
        Globals.exitCode = previousCode;
    }

    /** A new run: not exited, code 0. */
    public static void reset() {
        exited = false;
        Globals.exitCode = 0;
    }
}
