package app.specy.rars.riscv.io;

import app.specy.rars.util.JavaRandom;

/**
 * Everything a program reaches outside the simulator through its syscalls. The syscalls own each
 * service's semantics, as RARS defines them: they format what they print and parse what they
 * read, so an implementation only moves text and bytes.
 */
public abstract class RISCVIO {
    public abstract int openFile(String filename, int flags, boolean append) throws RISCVIOError;
    public abstract void closeFile(int fileDescriptor) throws RISCVIOError;

    /**
     * Writes {@code buffer} to an open file.
     *
     * @return the number of bytes written, or -1 when the write failed
     */
    public abstract int writeFile(int fileDescriptor, byte[] buffer) throws RISCVIOError;

    /**
     * Reads at most {@code length} bytes of an open file into {@code destination}.
     *
     * @return the number of bytes read, 0 at the end of the file, or -1 when the read failed
     */
    public abstract int readFile(int fileDescriptor, byte[] destination, int length) throws RISCVIOError;


    // 0 ---> meaning Yes
    // 1 ---> meaning No
    // 2 ---> meaning Cancel
    public abstract int confirm(String message);

    /**
     * Asks for a line of text in a dialog.
     *
     * @return the text entered, or null when the dialog was cancelled
     */
    public abstract String inputDialog(String message);

    /*
     *  ERROR_MESSAGE = 0
     *  INFORMATION_MESSAGE = 1
     *  WARNING_MESSAGE = 2
     *  QUESTION_MESSAGE = 3
     */
    public abstract void outputDialog(String message, int type);

    /** The line typed for read int (syscall 5), which the syscall trims and parses. */
    public abstract String readInt();

    /** The line typed for read float (syscall 6), which the syscall trims and parses. */
    public abstract String readFloat();

    /** The line typed for read double (syscall 7), which the syscall trims and parses. */
    public abstract String readDouble();

    public abstract String readString();

    /** What was typed for read char (syscall 12), whose first character the syscall takes. */
    public abstract String readChar();

    /** Program output: every print syscall formats its value and writes the text here. */
    public abstract void printString(String text);


    public abstract void sleep(int milliseconds);

    /**
     * Program time in milliseconds, the value the time syscall reports. It is the environment's
     * clock rather than the host's so that a scripted run can hand out a virtual clock instead of
     * the wall clock and stay reproducible.
     */
    public abstract double time();

    /**
     * Reads standard input into {@code buffer}: the bytes left over from the current line, or else
     * one new line from the user, never more than {@code length}.
     *
     * @return the number of bytes read, 0 at end of input, or -1 when the read failed
     */
    public abstract int stdIn(byte[] buffer, int length);

    /**
     * Moves an open file's position.
     *
     * @param whence 0 from the start, 1 from the current position, 2 from the end
     * @return the new position from the start of the file, or -1 when the seek failed
     */
    public abstract int seekFile(int fileDescriptor, int offset, int whence) throws RISCVIOError;

    public abstract void stdOut(byte[] buffer);

    public abstract void stdErr(byte[] buffer);

    /**
     * The seed a random generator starts from the first time a random service (41 to 44) uses it,
     * unless the program seeded it with service 40: a whole number from 0 to 2^48 - 1, as
     * {@code new java.util.Random(seed)} takes it. RARS starts such a generator from the host's
     * randomness, which this does; an environment that wants the same numbers on every run, a
     * scripted one, answers with a fixed seed instead.
     *
     * @param index the generator's number, as the program gave it in a0
     */
    public double randomSeed(int index) {
        return hostRandomSeed();
    }

    /** A seed from the host's randomness, what RARS's unseeded {@code new Random()} comes to. */
    public static double hostRandomSeed() {
        return Math.floor(Math.random() * JavaRandom.SEED_LIMIT);
    }
}
