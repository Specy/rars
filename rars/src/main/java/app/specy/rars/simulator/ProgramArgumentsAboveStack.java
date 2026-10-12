package app.specy.rars.simulator;

import app.specy.rars.Globals;
import app.specy.rars.riscv.hardware.AddressErrorException;
import app.specy.rars.riscv.hardware.Memory;
import app.specy.rars.riscv.hardware.RegisterFile;
import app.specy.rars.util.Utf8;

/**
 * A program's {@code argv}, {@code argv[0]} included, stored where it moves no register but
 * {@code a0} and {@code a1}: in the gap RARS leaves between the initial stack pointer
 * ({@code 0x7fffeffc} by default) and the stack base ({@code 0x7ffffffc}).
 * <p>
 * {@link ProgramArgumentList#storeProgramArguments()} writes the strings there too, but pushes
 * {@code argc} and the pointers below {@code sp} and lowers it, which changes the initial stack
 * pointer of every program run with arguments, and its pointers are always four bytes, which is
 * wrong for RV64. Here everything stays above the stack pointer:
 * <ul>
 * <li>the strings, UTF-8 and each ending in a NUL, {@code argv[0]} first, end at the stack base;</li>
 * <li>below them, aligned down to a pointer, the NULL-terminated array of pointers to them, four
 * bytes each on RV32 and eight on RV64, any byte between its end and the first string zero;</li>
 * <li>{@code a0} is {@code argc} and {@code a1} the array's address; {@code sp} keeps its value.</li>
 * </ul>
 * The data must end at least {@link #STACK_POINTER_CLEARANCE} bytes above the initial stack
 * pointer. Addresses come from the current memory configuration, the pointer size from the
 * current width ({@link Globals#is64Bit()}).
 */
public final class ProgramArgumentsAboveStack {
    /** The most words after {@code argv[0]}, the limit the x86 Core has too. */
    public static final int MAX_ARGUMENTS = 1024;

    /**
     * Bytes just above the initial stack pointer left to the program: what a program that stores
     * at {@code 0(sp)} before moving it writes, a word or a doubleword, with room to spare.
     */
    public static final int STACK_POINTER_CLEARANCE = 16;

    private ProgramArgumentsAboveStack() {
    }

    /** Bytes the program name, the Arguments and their {@code argv} array may take, at most. */
    public static long space() {
        return Math.max(0, top() - bottom());
    }

    /**
     * Why {@code argv} cannot be stored at the current width, as a sentence for the person who
     * typed it, or null when it can. An empty {@code argv} stores nothing and is no problem.
     */
    public static String problem(String[] argv) {
        return problem(argv, Globals.is64Bit());
    }

    /** {@link #problem(String[])} for the width {@code is64Bit} names rather than the current one. */
    public static String problem(String[] argv, boolean is64Bit) {
        if (argv == null) return "Arguments must be strings";
        for (String word : argv) {
            if (word == null) return "Arguments must be strings";
        }
        if (argv.length - 1 > MAX_ARGUMENTS) {
            return "Arguments may contain at most " + grouped(MAX_ARGUMENTS) + " words";
        }
        for (String word : argv) {
            if (word.indexOf('\0') >= 0) return "Arguments cannot contain NUL";
        }
        if (argv.length == 0) return null;
        long argvAddress = argvAddress(encode(argv), is64Bit ? 8 : Memory.WORD_LENGTH_BYTES);
        if (argvAddress < bottom()) {
            return "Arguments take " + grouped(top() - argvAddress)
                    + " bytes with the program name and argv; RISC-V programs have " + grouped(space())
                    + " bytes for them";
        }
        return null;
    }

    /**
     * Writes {@code argv} above the stack and sets {@code a0} and {@code a1}, directly: no write is
     * recorded for undo, so undoing to the program's start keeps them. An empty {@code argv}
     * changes nothing.
     *
     * @throws IllegalArgumentException with {@link #problem(String[])}'s sentence when it has one
     */
    public static void store(String[] argv) {
        String problem = problem(argv);
        if (problem != null) throw new IllegalArgumentException(problem);
        if (argv.length == 0) return;
        int pointerBytes = pointerBytes();
        byte[][] strings = encode(argv);
        long argvAddress = argvAddress(strings, pointerBytes);
        int address = (int) stringsAddress(strings);
        int[] pointers = new int[strings.length];
        try {
            for (int i = 0; i < strings.length; i++) {
                pointers[i] = address;
                for (byte b : strings[i]) {
                    Globals.memory.set(address++, b & 0xff, 1);
                }
                Globals.memory.set(address++, 0, 1);
            }
            int slot = (int) argvAddress;
            for (int pointer : pointers) {
                writePointer(slot, pointer, pointerBytes);
                slot += pointerBytes;
            }
            writePointer(slot, 0, pointerBytes);
            slot += pointerBytes;
            // the alignment's padding, which a run before this one may have written over
            for (int end = pointers[0]; slot < end; slot++) {
                Globals.memory.set(slot, 0, 1);
            }
        } catch (AddressErrorException e) {
            throw new IllegalStateException("The program's arguments could not be stored: " + e.getMessage(), e);
        }
        // straight to the registers, past the back stepper, as storeProgramArguments does
        RegisterFile.getRegister("a0").setValue(argv.length);
        RegisterFile.getRegister("a1").setValue(argvAddress);
    }

    /** A pointer, zero-extended to eight bytes on RV64, in RARS's little-endian order. */
    private static void writePointer(int address, int pointer, int pointerBytes) throws AddressErrorException {
        Globals.memory.set(address, pointer, Memory.WORD_LENGTH_BYTES);
        if (pointerBytes == 8) Globals.memory.set(address + Memory.WORD_LENGTH_BYTES, 0, Memory.WORD_LENGTH_BYTES);
    }

    private static int pointerBytes() {
        return Globals.is64Bit() ? 8 : Memory.WORD_LENGTH_BYTES;
    }

    /** One past the last byte the data may take: the stack base. */
    private static long top() {
        return Integer.toUnsignedLong(Memory.stackBaseAddress);
    }

    /** The lowest address the data may take. */
    private static long bottom() {
        return Integer.toUnsignedLong(Memory.stackPointer) + STACK_POINTER_CLEARANCE;
    }

    private static byte[][] encode(String[] argv) {
        byte[][] strings = new byte[argv.length][];
        for (int i = 0; i < argv.length; i++) {
            strings[i] = Utf8.encode(argv[i]);
        }
        return strings;
    }

    private static long stringsAddress(byte[][] strings) {
        long bytes = 0;
        for (byte[] string : strings) {
            bytes += string.length + 1;
        }
        return top() - bytes;
    }

    private static long argvAddress(byte[][] strings, int pointerBytes) {
        long start = stringsAddress(strings) - (long) (strings.length + 1) * pointerBytes;
        return Math.floorDiv(start, pointerBytes) * pointerBytes;
    }

    /** {@code 4080} as {@code 4,080}. */
    private static String grouped(long value) {
        String digits = Long.toString(value);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) out.append(',');
            out.append(digits.charAt(i));
        }
        return out.toString();
    }
}
