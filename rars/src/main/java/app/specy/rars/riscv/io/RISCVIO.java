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

    public abstract void stdIn(byte[] buffer, int length);

    public abstract void stdOut(byte[] buffer);

    public abstract void stdErr(byte[] buffer);
}


