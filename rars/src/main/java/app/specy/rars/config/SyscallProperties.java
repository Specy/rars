package app.specy.rars.config;

public class SyscallProperties extends ConfigMap {
    public static final String LSeek = "LSeek";
    public static final String GetCWD = "GetCWD";
    public static final String PrintInt = "PrintInt";
    public static final String PrintFloat = "PrintFloat";
    public static final String PrintDouble = "PrintDouble";
    public static final String PrintString = "PrintString";
    public static final String ReadInt = "ReadInt";
    public static final String ReadFloat = "ReadFloat";
    public static final String ReadDouble = "ReadDouble";
    public static final String ReadString = "ReadString";
    public static final String Sbrk = "Sbrk";
    public static final String Exit = "Exit";
    public static final String PrintChar = "PrintChar";
    public static final String ReadChar = "ReadChar";
    public static final String Open = "Open";
    public static final String Read = "Read";
    public static final String Write = "Write";
    public static final String Close = "Close";
    public static final String Exit2 = "Exit2";
    public static final String Time = "Time";
    public static final String PrintIntHex = "PrintIntHex";
    public static final String PrintIntBinary = "PrintIntBinary";
    public static final String PrintIntUnsigned = "PrintIntUnsigned";
    public static final String RandInt = "RandInt";
    public static final String RandIntRange = "RandIntRange";
    public static final String RandFloat = "RandFloat";
    public static final String RandDouble = "RandDouble";
    public static final String ConfirmDialog = "ConfirmDialog";
    public static final String InputDialogInt = "InputDialogInt";
    public static final String InputDialogFloat = "InputDialogFloat";
    public static final String InputDialogDouble = "InputDialogDouble";
    public static final String InputDialogString = "InputDialogString";
    public static final String MessageDialog = "MessageDialog";
    public static final String MessageDialogInt = "MessageDialogInt";
    public static final String MessageDialogFloat = "MessageDialogFloat";
    public static final String MessageDialogDouble = "MessageDialogDouble";
    public static final String MessageDialogString = "MessageDialogString";

    public SyscallProperties() {
        reset();
    }

    public void reset() {
        put(GetCWD, "17");
        //put(LSeek, "62");

        put(PrintInt, "1");
        put(PrintFloat, "2");
        put(PrintDouble, "3");
        put(PrintString, "4");
        put(ReadInt, "5");
        put(ReadFloat, "6");
        put(ReadDouble, "7");
        put(ReadString, "8");
        put(Sbrk, "9");
        put(Exit, "10");
        put(PrintChar, "11");
        put(ReadChar, "12");
        put(Open, "1024");
        put(Read, "63");
        put(Write, "64");
        put(Close, "57");
        put(Exit2, "93");
        put(Time, "30");
        put(PrintIntHex, "34");
        put(PrintIntBinary, "35");
        put(PrintIntUnsigned, "36");
        put(RandInt, "41");
        put(RandIntRange, "42");
        put(RandFloat, "43");
        put(RandDouble, "44");
        put(ConfirmDialog, "50");
        put(InputDialogInt, "51");
        put(InputDialogFloat, "52");
        put(InputDialogDouble, "53");
        put(InputDialogString, "54");
        put(MessageDialog, "55");
        put(MessageDialogInt, "56");
        put(MessageDialogDouble, "58");
        put(MessageDialogString, "59");
        put(MessageDialogFloat, "60");
    }
}
