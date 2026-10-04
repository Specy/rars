package app.specy.rars.riscv.io;

public abstract class RISCVIO {
    public abstract int openFile(String filename, int flags, boolean append) throws RISCVIOError;
    public abstract void closeFile(int fileDescriptor) throws RISCVIOError;
    public abstract void writeFile(int fileDescriptor, byte[] buffer) throws RISCVIOError;
    public abstract int readFile(int fileDescriptor, byte[] destination, int length) throws RISCVIOError;


    // 0 ---> meaning Yes
    // 1 ---> meaning No
    // 2 ---> meaning Cancel
    public abstract int confirm(String message);

    public abstract String inputDialog(String message);

    /*
     *  ERROR_MESSAGE = 0
     *  INFORMATION_MESSAGE = 1
     *  WARNING_MESSAGE = 2
     *  QUESTION_MESSAGE = 3
     */
    public abstract void outputDialog(String message, int type);

    public abstract double askDouble(String message);

    public abstract float askFloat(String message);

    public abstract int askInt(String message);

    public abstract String askString(String message);

    public abstract double readDouble();

    public abstract float readFloat();

    public abstract int readInt();

    public abstract String readString();    

    public abstract char readChar();

    public abstract void logLine(String message);

    public abstract void log(String message);

    public abstract void printChar(char c);

    public abstract void printDouble(double d);

    public abstract void printFloat(float f);

    public abstract void printInt(int i);

    public abstract void printString(String l);


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
}


