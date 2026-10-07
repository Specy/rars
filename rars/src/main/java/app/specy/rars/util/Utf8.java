package app.specy.rars.util;

/**
 * UTF-8 as Java 21 encodes and decodes a string: {@code String.getBytes(StandardCharsets.UTF_8)} and
 * {@code new String(bytes, StandardCharsets.UTF_8)}. It is written out here, rather than left to
 * the TeaVM runtime the simulator is compiled with, so that text that is not well formed comes out
 * as it does on the JDK:
 * <ul>
 * <li>encoding writes each character by code point, a surrogate pair as one four byte sequence and
 * a surrogate without its pair as {@code ?};</li>
 * <li>decoding replaces each malformed sequence with U+FFFD, one for every maximal part of a
 * sequence that could have started a character, as the JDK's decoder does.</li>
 * </ul>
 */
public final class Utf8 {
    private static final char REPLACEMENT = '�';

    private Utf8() {
    }

    /** {@code text.toString().getBytes(StandardCharsets.UTF_8)} of Java 21. */
    public static byte[] encode(CharSequence text) {
        int length = text.length();
        // A character takes at most three bytes, and a pair of them four.
        byte[] out = new byte[length * 3];
        int position = 0;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c < 0x80) {
                out[position++] = (byte) c;
            } else if (c < 0x800) {
                out[position++] = (byte) (0xc0 | (c >> 6));
                out[position++] = (byte) (0x80 | (c & 0x3f));
            } else if (c >= 0xd800 && c <= 0xdfff) {
                char next = i + 1 < length ? text.charAt(i + 1) : 0;
                if (c <= 0xdbff && next >= 0xdc00 && next <= 0xdfff) {
                    int codePoint = ((c - 0xd800) << 10) + (next - 0xdc00) + 0x10000;
                    out[position++] = (byte) (0xf0 | (codePoint >> 18));
                    out[position++] = (byte) (0x80 | ((codePoint >> 12) & 0x3f));
                    out[position++] = (byte) (0x80 | ((codePoint >> 6) & 0x3f));
                    out[position++] = (byte) (0x80 | (codePoint & 0x3f));
                    i++;
                } else {
                    out[position++] = '?';
                }
            } else {
                out[position++] = (byte) (0xe0 | (c >> 12));
                out[position++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                out[position++] = (byte) (0x80 | (c & 0x3f));
            }
        }
        byte[] exact = new byte[position];
        System.arraycopy(out, 0, exact, 0, position);
        return exact;
    }

    /** {@code new String(bytes, StandardCharsets.UTF_8)} of Java 21. */
    public static String decode(byte[] bytes) {
        return decode(bytes, 0, bytes.length);
    }

    /**
     * {@code new String(bytes, offset, length, StandardCharsets.UTF_8)} of Java 21, which follows
     * {@code java.lang.String.decodeUTF8_UTF16} step for step, the end of the input included.
     */
    public static String decode(byte[] bytes, int offset, int length) {
        // Every byte gives at most one character, and a four byte sequence two.
        char[] out = new char[length];
        int count = 0;
        int position = offset;
        int end = offset + length;
        while (position < end) {
            int b1 = bytes[position++];
            if (b1 >= 0) {
                out[count++] = (char) b1;
            } else if ((b1 >> 5) == -2 && (b1 & 0x1e) != 0) {
                // C2 to DF: two bytes.
                if (position < end) {
                    int b2 = bytes[position++];
                    if (notContinuation(b2)) {
                        out[count++] = REPLACEMENT;
                        position--;
                    } else {
                        out[count++] = (char) (((b1 & 0x1f) << 6) | (b2 & 0x3f));
                    }
                    continue;
                }
                out[count++] = REPLACEMENT;
                break;
            } else if ((b1 >> 4) == -2) {
                // E0 to EF: three bytes.
                if (position + 1 < end) {
                    int b2 = bytes[position++];
                    int b3 = bytes[position++];
                    if (malformed3(b1, b2) || notContinuation(b3)) {
                        out[count++] = REPLACEMENT;
                        // Only the lead byte, or the lead byte and its first continuation, are the
                        // malformed part; decoding resumes after it.
                        position -= malformed3(b1, b2) ? 2 : 1;
                    } else {
                        char c = (char) (((b1 & 0x0f) << 12) | ((b2 & 0x3f) << 6) | (b3 & 0x3f));
                        out[count++] = c >= 0xd800 && c <= 0xdfff ? REPLACEMENT : c;
                    }
                    continue;
                }
                if (position < end && malformed3(b1, bytes[position])) {
                    out[count++] = REPLACEMENT;
                    continue;
                }
                out[count++] = REPLACEMENT;
                break;
            } else if ((b1 >> 3) == -2) {
                // F0 to F7: four bytes, of which F5 to F7 never start a character.
                if (position + 2 < end) {
                    int b2 = bytes[position++];
                    int b3 = bytes[position++];
                    int b4 = bytes[position++];
                    int codePoint = ((b1 & 0x07) << 18) | ((b2 & 0x3f) << 12) | ((b3 & 0x3f) << 6) | (b4 & 0x3f);
                    if (notContinuation(b2) || notContinuation(b3) || notContinuation(b4)
                            || codePoint < 0x10000 || codePoint > 0x10ffff) {
                        out[count++] = REPLACEMENT;
                        position -= 3;
                        if (!malformed4(b1 & 0xff, b2 & 0xff)) {
                            position += notContinuation(b3) ? 1 : 2;
                        }
                    } else {
                        out[count++] = (char) (0xd800 + ((codePoint - 0x10000) >> 10));
                        out[count++] = (char) (0xdc00 + ((codePoint - 0x10000) & 0x3ff));
                    }
                    continue;
                }
                int lead = b1 & 0xff;
                if (lead > 0xf4 || position < end && malformed4(lead, bytes[position] & 0xff)) {
                    out[count++] = REPLACEMENT;
                    continue;
                }
                position++;
                out[count++] = REPLACEMENT;
                if (position < end && notContinuation(bytes[position])) {
                    continue;
                }
                break;
            } else {
                // A continuation byte with nothing before it, C0, C1 or F8 to FF.
                out[count++] = REPLACEMENT;
            }
        }
        return new String(out, 0, count);
    }

    private static boolean notContinuation(int b) {
        return (b & 0xc0) != 0x80;
    }

    /** Whether the second byte of a three byte sequence already makes it malformed. */
    private static boolean malformed3(int b1, int b2) {
        return (b1 == (byte) 0xe0 && (b2 & 0xe0) == 0x80) || notContinuation(b2);
    }

    /** Whether the second byte of a four byte sequence already makes it malformed, both unsigned. */
    private static boolean malformed4(int b1, int b2) {
        return b1 > 0xf4
                || (b1 == 0xf0 && (b2 < 0x90 || b2 > 0xbf))
                || (b1 == 0xf4 && (b2 & 0xf0) != 0x80)
                || notContinuation(b2);
    }
}
