package app.specy.rars.util;

/**
 * Numbers as text, exactly as Java 21 writes and reads them. RARS prints and parses numbers with
 * the JDK it runs on, and TeaVM's own {@code Float.toString}, {@code Double.toString} and
 * {@code Double.parseDouble} differ from a current JDK on edge values, so the syscalls use these:
 * <ul>
 * <li>{@link #toString(float)} and {@link #toString(double)} follow the specification of JDK 19
 * and later: of the decimals that round to the value, the shortest, and of those the closest to
 * it, the even one on a tie; when the shortest has one digit, the closest of one or two digits.
 * The layout is Java's: {@code 1.0}, {@code 0.001}, {@code 1.0E-4}, {@code 1.0E7}, {@code -0.0},
 * {@code NaN}, {@code Infinity}.</li>
 * <li>{@link #parseInt(String)} is {@code Integer.parseInt}, with every decimal digit Java
 * accepts.</li>
 * <li>{@link #parseFloat(String)} and {@link #parseDouble(String)} accept Java's grammar (trimmed,
 * an optional sign, a decimal or hexadecimal significand, an optional {@code f}, {@code F},
 * {@code d} or {@code D} suffix, {@code NaN}, {@code Infinity}) and round correctly, a float
 * directly rather than through a double.</li>
 * </ul>
 * Big intermediate values live in {@link Natural}, which uses no {@code long}: TeaVM compiles
 * {@code long} to a BigInt that allocates on every operation.
 */
public final class JavaNumberText {

    private JavaNumberText() {
    }

    // ---------------------------------------------------------------------------------------
    // Formatting

    /** {@code Float.toString} of Java 21. */
    public static String toString(float value) {
        int bits = Float.floatToRawIntBits(value);
        boolean negative = bits < 0;
        int exponentField = (bits >>> 23) & 0xff;
        int fraction = bits & 0x7fffff;
        if (exponentField == 0xff) {
            return fraction != 0 ? "NaN" : negative ? "-Infinity" : "Infinity";
        }
        if (exponentField == 0 && fraction == 0) {
            return negative ? "-0.0" : "0.0";
        }
        int significand = exponentField == 0 ? fraction : fraction | 0x800000;
        int exponent = exponentField == 0 ? -149 : exponentField - 150;
        boolean binadeStart = exponentField > 1 && fraction == 0;
        Decimal decimal = shortest(0, significand, exponent, binadeStart, (significand & 1) == 0,
                Math.abs((double) value));
        return decimal.layout(negative);
    }

    /** {@code Double.toString} of Java 21. */
    public static String toString(double value) {
        long bits = Double.doubleToRawLongBits(value);
        int high = (int) (bits >>> 32);
        int low = (int) bits;
        boolean negative = high < 0;
        int exponentField = (high >>> 20) & 0x7ff;
        int fractionHigh = high & 0xfffff;
        if (exponentField == 0x7ff) {
            return (fractionHigh | low) != 0 ? "NaN" : negative ? "-Infinity" : "Infinity";
        }
        if (exponentField == 0 && (fractionHigh | low) == 0) {
            return negative ? "-0.0" : "0.0";
        }
        int significandHigh = exponentField == 0 ? fractionHigh : fractionHigh | 0x100000;
        int exponent = exponentField == 0 ? -1074 : exponentField - 1075;
        boolean binadeStart = exponentField > 1 && (fractionHigh | low) == 0;
        Decimal decimal = shortest(significandHigh, low, exponent, binadeStart, (low & 1) == 0,
                Math.abs(value));
        return decimal.layout(negative);
    }

    /**
     * The decimal that represents {@code significand * 2^exponent}, by digit generation on exact
     * integers (Steele and White's free-format algorithm as Burger and Dybvig refined it).
     *
     * @param high        the significand's bits above the low 32
     * @param low         the significand's low 32 bits
     * @param binadeStart whether the value is the first of its binade, so that the gap to the
     *                    value below is half the gap to the value above
     * @param even        whether the significand is even: round half to even then gives this
     *                    value the midpoints with its neighbours, so they are in range
     * @param magnitude   the value itself, only to estimate the decimal exponent
     */
    private static Decimal shortest(int high, int low, int exponent, boolean binadeStart,
                                    boolean even, double magnitude) {
        // Scale by 10^-k, for the least k whose power of ten the top of the range stays below
        // (or does not pass, when the top is excluded): the first digit is then the 10^(k-1)
        // one, and rounding the last digit up never carries past it. This is an estimate, which
        // the loops below correct.
        int k = (int) Math.ceil(Math.log10(magnitude));
        // Room for the values below, so that they seldom grow: the powers of two and of ten, a
        // 53 bit significand, and the factors of ten the digit loop adds.
        int capacity = (Math.abs(exponent) + 4 * Math.abs(k) + 160) / 27;

        // The value is r / s, and the decimals that round to it lie within mMinus / s below it
        // and mPlus / s above it: the half gaps to its neighbours.
        Natural r = Natural.of(high, low, capacity);
        Natural s;
        Natural mPlus;
        int shift = binadeStart ? 2 : 1;
        if (exponent >= 0) {
            r.shiftLeft(exponent + shift);
            s = Natural.of(0, 1 << shift, capacity);
            mPlus = Natural.of(0, 1, capacity);
            mPlus.shiftLeft(exponent + shift - 1);
        } else {
            r.shiftLeft(shift);
            s = Natural.of(0, 1, capacity);
            s.shiftLeft(shift - exponent);
            mPlus = Natural.of(0, 1 << (shift - 1), capacity);
        }
        Natural mMinus = mPlus;
        if (binadeStart) {
            mMinus = mPlus.copy();
            mMinus.shiftRightOne();
        }
        // Reused for every sum and doubling below, so that the digit loop allocates nothing.
        Natural scratch = new Natural(capacity);

        if (k >= 0) {
            s.multiplyPow10(k);
        } else {
            r.multiplyPow10(-k);
            mPlus.multiplyPow10(-k);
            if (mMinus != mPlus) {
                mMinus.multiplyPow10(-k);
            }
        }
        while (reachesWhole(scratch.setSum(r, mPlus), s, even)) {
            s.multiply(10);
            k++;
        }
        while (true) {
            scratch.setSum(r, mPlus).multiply(10);
            if (reachesWhole(scratch, s, even)) {
                break;
            }
            r.multiply(10);
            mPlus.multiply(10);
            if (mMinus != mPlus) {
                mMinus.multiply(10);
            }
            k--;
        }

        char[] digits = new char[20];
        int length = 0;
        int firstDigit = 0;
        while (true) {
            r.multiply(10);
            mPlus.multiply(10);
            if (mMinus != mPlus) {
                mMinus.multiply(10);
            }
            int digit = 0;
            while (r.compareTo(s) >= 0) {
                r.subtract(s);
                digit++;
            }
            if (length == 0) {
                firstDigit = digit;
            }
            boolean downInRange = withinGap(r, mMinus, even);
            boolean upInRange = reachesWhole(scratch.setSum(r, mPlus), s, even);
            if (!downInRange && !upInRange) {
                digits[length++] = (char) ('0' + digit);
                continue;
            }
            if (downInRange && upInRange) {
                // The digit and the one above it both round to the value: the closer, or the
                // even one when the value lies exactly between them.
                int half = scratch.setTwice(r).compareTo(s);
                if (half > 0 || (half == 0 && (digit & 1) == 1)) {
                    digit++;
                }
            } else if (upInRange) {
                digit++;
            }
            digits[length++] = (char) ('0' + digit);
            break;
        }
        if (length == 1) {
            return closestOfOneOrTwoDigits(r, s, mPlus, mMinus, even, k, firstDigit, scratch);
        }
        return new Decimal(digits, length, k - 1);
    }

    /**
     * When the shortest decimal in range has one digit, Java takes the decimal of one or two
     * digits in range that is closest to the value. The closest two digit decimals are the ones
     * just below and just above the value on the grid of its second digit, and at least one of
     * them is in range because a one digit decimal is.
     *
     * @param r          what is left of the value after its 10^(k-1) digit {@code firstDigit},
     *                   as {@code r / s} of that digit's unit, with {@code mPlus} and
     *                   {@code mMinus} its half gaps on the same scale; all three are consumed
     * @param firstDigit the 10^(k-1) digit before rounding, 0 when the value lies below 10^(k-1)
     *                   and only its range reaches that power of ten
     */
    private static Decimal closestOfOneOrTwoDigits(Natural r, Natural s, Natural mPlus,
                                                   Natural mMinus, boolean even, int k,
                                                   int firstDigit, Natural scratch) {
        int exponent = firstDigit > 0 ? k - 1 : k - 2;
        // The value's first two digits, below * 10^(exponent - 1) <= value.
        int below = firstDigit;
        for (int step = firstDigit > 0 ? 1 : 2; step > 0; step--) {
            r.multiply(10);
            mPlus.multiply(10);
            if (mMinus != mPlus) {
                mMinus.multiply(10);
            }
            int digit = 0;
            while (r.compareTo(s) >= 0) {
                r.subtract(s);
                digit++;
            }
            below = below * 10 + digit;
        }
        // The value lies r / s grid steps above below * 10^(exponent - 1).
        int chosen;
        if (r.isZero()) {
            chosen = below;
        } else {
            boolean belowInRange = withinGap(r, mMinus, even);
            boolean aboveInRange = reachesWhole(scratch.setSum(r, mPlus), s, even);
            if (belowInRange && aboveInRange) {
                int half = scratch.setTwice(r).compareTo(s);
                if (half == 0) {
                    chosen = (withoutTrailingZeros(below) & 1) == 0 ? below : below + 1;
                } else {
                    chosen = half < 0 ? below : below + 1;
                }
            } else {
                chosen = belowInRange ? below : below + 1;
            }
        }
        if (chosen == 100) {
            return new Decimal(new char[]{'1'}, 1, exponent + 1);
        }
        if (chosen % 10 == 0) {
            return new Decimal(new char[]{(char) ('0' + chosen / 10)}, 1, exponent);
        }
        return new Decimal(new char[]{(char) ('0' + chosen / 10), (char) ('0' + chosen % 10)}, 2,
                exponent);
    }

    /** Whether {@code part} reaches {@code whole}: at least it when inclusive, beyond it if not. */
    private static boolean reachesWhole(Natural part, Natural whole, boolean inclusive) {
        int compare = part.compareTo(whole);
        return inclusive ? compare >= 0 : compare > 0;
    }

    /** Whether a remainder is within the half gap: no more than it when inclusive, less if not. */
    private static boolean withinGap(Natural remainder, Natural gap, boolean inclusive) {
        int compare = remainder.compareTo(gap);
        return inclusive ? compare <= 0 : compare < 0;
    }

    private static int withoutTrailingZeros(int value) {
        while (value % 10 == 0) {
            value /= 10;
        }
        return value;
    }

    /** Digits d1...dn, neither end zero, of the value d1.d2...dn * 10^exponent. */
    private static final class Decimal {
        private final char[] digits;
        private final int length;
        private final int exponent;

        Decimal(char[] digits, int length, int exponent) {
            this.digits = digits;
            this.length = length;
            this.exponent = exponent;
        }

        /** Plain notation from 10^-3 up to 10^7, computerized scientific notation outside it. */
        String layout(boolean negative) {
            StringBuilder text = new StringBuilder(length + 12);
            if (negative) {
                text.append('-');
            }
            if (exponent >= -3 && exponent < 0) {
                text.append("0.");
                for (int zero = exponent + 1; zero < 0; zero++) {
                    text.append('0');
                }
                text.append(digits, 0, length);
            } else if (exponent >= 0 && exponent < 7) {
                if (length <= exponent + 1) {
                    text.append(digits, 0, length);
                    for (int zero = length; zero <= exponent; zero++) {
                        text.append('0');
                    }
                    text.append(".0");
                } else {
                    text.append(digits, 0, exponent + 1);
                    text.append('.');
                    text.append(digits, exponent + 1, length - exponent - 1);
                }
            } else {
                text.append(digits[0]);
                text.append('.');
                if (length == 1) {
                    text.append('0');
                } else {
                    text.append(digits, 1, length - 1);
                }
                text.append('E');
                text.append(exponent);
            }
            return text.toString();
        }
    }

    // ---------------------------------------------------------------------------------------
    // Parsing

    /**
     * The first character of each run of ten decimal digits that Java 21 (Unicode 15.0) knows in
     * the Basic Multilingual Plane, after ASCII's. {@code Integer.parseInt} reads one char at a
     * time, so the digits of the supplementary planes never count.
     */
    private static final char[] DIGIT_ZEROS = {
            '\u0660', '\u06F0', '\u07C0', '\u0966', '\u09E6', '\u0A66', '\u0AE6', '\u0B66',
            '\u0BE6', '\u0C66', '\u0CE6', '\u0D66', '\u0DE6', '\u0E50', '\u0ED0', '\u0F20',
            '\u1040', '\u1090', '\u17E0', '\u1810', '\u1946', '\u19D0', '\u1A80', '\u1A90',
            '\u1B50', '\u1BB0', '\u1C40', '\u1C50', '\uA620', '\uA8D0', '\uA900', '\uA9D0',
            '\uA9F0', '\uAA50', '\uABF0', '\uFF10',
    };

    /** {@code Character.digit(c, 10)} of Java 21. */
    private static int decimalDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        for (char zero : DIGIT_ZEROS) {
            if (c >= zero && c <= zero + 9) {
                return c - zero;
            }
        }
        return -1;
    }

    /**
     * {@code Character.digit(c, radix)} of Java 21: a decimal digit of any script, or a Latin letter,
     * ASCII or fullwidth, for the values from 10 up.
     */
    private static int digit(char c, int radix) {
        int value;
        if (c >= 'A' && c <= 'Z') {
            value = c - 'A' + 10;
        } else if (c >= 'a' && c <= 'z') {
            value = c - 'a' + 10;
        } else if (c >= 'Ａ' && c <= 'Ｚ') {
            value = c - 'Ａ' + 10;
        } else if (c >= 'ａ' && c <= 'ｚ') {
            value = c - 'ａ' + 10;
        } else {
            value = decimalDigit(c);
        }
        return value < radix ? value : -1;
    }

    /** {@code Integer.parseInt(text)} of Java 21. */
    public static int parseInt(String text) throws NumberFormatException {
        return parseInt(text, 10);
    }

    /**
     * {@code Integer.parseInt(text, radix)} of Java 21, for a radix from 2 to 36: the assemblers
     * read the four digits of a {@code \}{@code u} escape with radix 16, as RARS does.
     */
    public static int parseInt(String text, int radix) throws NumberFormatException {
        int length = text.length();
        if (length == 0) {
            throw invalid(text);
        }
        int index = 0;
        boolean negative = false;
        int limit = -Integer.MAX_VALUE;
        char first = text.charAt(0);
        if (first < '0') {
            if (first == '-') {
                negative = true;
                limit = Integer.MIN_VALUE;
            } else if (first != '+') {
                throw invalid(text);
            }
            if (length == 1) {
                throw invalid(text);
            }
            index++;
        }
        // Accumulated negatively, as Java does, so that the minimum value has room.
        int multiplicationLimit = limit / radix;
        int result = 0;
        while (index < length) {
            int digit = digit(text.charAt(index++), radix);
            if (digit < 0 || result < multiplicationLimit) {
                throw invalid(text);
            }
            result *= radix;
            if (result < limit + digit) {
                throw invalid(text);
            }
            result -= digit;
        }
        return negative ? result : -result;
    }

    /** {@code Float.parseFloat(text)} of Java 21. */
    public static float parseFloat(String text) throws NumberFormatException {
        return Float.intBitsToFloat((int) new Parser(text, FLOAT).parse());
    }

    /** {@code Double.parseDouble(text)} of Java 21. */
    public static double parseDouble(String text) throws NumberFormatException {
        return Double.longBitsToDouble(new Parser(text, DOUBLE).parse());
    }

    /** A binary floating point format. */
    private static final class Format {
        /** Significand bits, the leading one included. */
        final int precision;
        /** The exponent of the smallest subnormal's only bit. */
        final int minimumExponent;
        /** The largest unbiased exponent of a finite value. */
        final int maximumExponent;
        /** A value of 10^overflowDecade or more is infinite whatever its digits. */
        final int overflowDecade;
        /** A value below 10^-underflowDecade is zero whatever its digits. */
        final int underflowDecade;
        final long infinity;
        final long nan;
        final long sign;

        Format(int precision, int minimumExponent, int maximumExponent, int overflowDecade,
               int underflowDecade, long infinity, long nan, long sign) {
            this.precision = precision;
            this.minimumExponent = minimumExponent;
            this.maximumExponent = maximumExponent;
            this.overflowDecade = overflowDecade;
            this.underflowDecade = underflowDecade;
            this.infinity = infinity;
            this.nan = nan;
            this.sign = sign;
        }
    }

    private static final Format FLOAT = new Format(24, -149, 127, 40, 46,
            0x7f800000L, 0x7fc00000L, 0x80000000L);
    private static final Format DOUBLE = new Format(53, -1074, 1023, 310, 325,
            0x7ff0000000000000L, 0x7ff8000000000000L, 0x8000000000000000L);

    /**
     * More significant digits than any value halfway between two doubles has (under 800), so
     * replacing the digits after them by a single non-zero one never changes the rounding.
     */
    private static final int KEPT_DIGITS = 1100;

    /** Exponents are clamped here: anything larger leaves every value zero or infinite. */
    private static final int EXPONENT_CLAMP = 100_000_000;

    /** One parse of a trimmed text, following {@code FloatingDecimal.readJavaFormatString}. */
    private static final class Parser {
        private final String original;
        private final String text;
        private final Format format;
        private int index;
        private long sign;

        Parser(String original, Format format) {
            this.original = original;
            this.text = original.trim();
            this.format = format;
        }

        long parse() {
            int length = text.length();
            if (length == 0) {
                throw invalid(original);
            }
            char first = text.charAt(0);
            if (first == '-' || first == '+') {
                sign = first == '-' ? format.sign : 0;
                index++;
                if (index == length) {
                    throw invalid(original);
                }
            }
            char lead = text.charAt(index);
            if (lead == 'N') {
                if (text.substring(index).equals("NaN")) {
                    return format.nan;
                }
                throw invalid(original);
            }
            if (lead == 'I') {
                if (text.substring(index).equals("Infinity")) {
                    return sign | format.infinity;
                }
                throw invalid(original);
            }
            if (lead == '0' && index + 1 < length
                    && (text.charAt(index + 1) == 'x' || text.charAt(index + 1) == 'X')) {
                index += 2;
                return hexadecimal();
            }
            return decimal();
        }

        /** {@code Digits . Digits ExponentPart Suffix}, with the parts Java lets go missing. */
        private long decimal() {
            StringBuilder significant = new StringBuilder();
            int[] counts = scanDigits(significant, false);
            int fractionDigits = counts[0];
            int trailingZeros = counts[1];
            int exponent = 0;
            if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                index++;
                exponent = signedExponent();
            }
            checkSuffix();
            if (significant.length() == 0) {
                return sign;
            }
            // The value is significant * 10^decimalExponent and lies below 10^leadingDecade.
            long decimalExponent = (long) exponent - fractionDigits + trailingZeros;
            int digitCount = significant.length();
            long leadingDecade = digitCount + decimalExponent;
            if (leadingDecade > format.overflowDecade) {
                return sign | format.infinity;
            }
            if (leadingDecade < -format.underflowDecade) {
                return sign;
            }
            if (digitCount > KEPT_DIGITS) {
                decimalExponent += digitCount - (KEPT_DIGITS + 1);
                significant.setLength(KEPT_DIGITS);
                significant.append('1');
            }
            Natural numerator = Natural.ofDigits(significant, 10);
            Natural denominator = Natural.of(0, 1);
            if (decimalExponent >= 0) {
                numerator.multiplyPow10((int) decimalExponent);
            } else {
                denominator.multiplyPow10((int) -decimalExponent);
            }
            return sign | round(numerator, denominator, format);
        }

        /** {@code HexDigits . HexDigits (p|P) SignedInteger Suffix}, after the {@code 0x}. */
        private long hexadecimal() {
            StringBuilder significant = new StringBuilder();
            int[] counts = scanDigits(significant, true);
            // Unlike a decimal one, a hexadecimal significand needs its binary exponent.
            if (index == text.length() || (text.charAt(index) != 'p' && text.charAt(index) != 'P')) {
                throw invalid(original);
            }
            index++;
            int exponent = signedExponent();
            checkSuffix();
            if (significant.length() == 0) {
                return sign;
            }
            long binaryExponent = (long) exponent - 4L * counts[0] + 4L * counts[1];
            int digitCount = significant.length();
            if (digitCount > KEPT_DIGITS) {
                binaryExponent += 4L * (digitCount - (KEPT_DIGITS + 1));
                significant.setLength(KEPT_DIGITS);
                significant.append('1');
            }
            Natural numerator = Natural.ofDigits(significant, 16);
            // The value lies below 2^leadingBit and at or above half of it.
            long leadingBit = numerator.bitLength() + binaryExponent;
            if (leadingBit > format.maximumExponent + 1) {
                return sign | format.infinity;
            }
            if (leadingBit < format.minimumExponent - 1) {
                return sign;
            }
            Natural denominator = Natural.of(0, 1);
            if (binaryExponent >= 0) {
                numerator.shiftLeft((int) binaryExponent);
            } else {
                denominator.shiftLeft((int) -binaryExponent);
            }
            return sign | round(numerator, denominator, format);
        }

        /**
         * Reads digits with at most one point, keeping the significant ones (no zero at either
         * end) in {@code significant}.
         *
         * @return the number of digits after the point, and the zeros dropped from the end
         */
        private int[] scanDigits(StringBuilder significant, boolean hexadecimal) {
            int length = text.length();
            boolean point = false;
            boolean anyDigit = false;
            int fractionDigits = 0;
            int pendingZeros = 0;
            for (; index < length; index++) {
                char c = text.charAt(index);
                int value = hexadecimal ? hexDigit(c) : c >= '0' && c <= '9' ? c - '0' : -1;
                if (c == '.') {
                    if (point) {
                        throw invalid(original);
                    }
                    point = true;
                } else if (value >= 0) {
                    anyDigit = true;
                    if (point) {
                        fractionDigits++;
                    }
                    if (value == 0) {
                        // A zero counts only once a non-zero digit follows it.
                        if (significant.length() > 0) {
                            pendingZeros++;
                        }
                    } else {
                        for (; pendingZeros > 0; pendingZeros--) {
                            significant.append('0');
                        }
                        significant.append(c);
                    }
                } else {
                    break;
                }
            }
            if (!anyDigit) {
                throw invalid(original);
            }
            return new int[]{fractionDigits, pendingZeros};
        }

        /** An optionally signed run of ASCII digits, clamped. */
        private int signedExponent() {
            int length = text.length();
            if (index == length) {
                throw invalid(original);
            }
            boolean negative = false;
            char c = text.charAt(index);
            if (c == '-' || c == '+') {
                negative = c == '-';
                index++;
            }
            int start = index;
            int value = 0;
            for (; index < length; index++) {
                c = text.charAt(index);
                if (c < '0' || c > '9') {
                    break;
                }
                value = Math.min(value * 10 + (c - '0'), EXPONENT_CLAMP);
            }
            if (index == start) {
                throw invalid(original);
            }
            return negative ? -value : value;
        }

        /** Nothing may follow but one of the suffixes {@code f}, {@code F}, {@code d}, {@code D}. */
        private void checkSuffix() {
            int length = text.length();
            if (index == length) {
                return;
            }
            char c = text.charAt(index);
            if (index != length - 1 || (c != 'f' && c != 'F' && c != 'd' && c != 'D')) {
                throw invalid(original);
            }
        }
    }

    private static int hexDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    /**
     * The bits of the positive value {@code numerator / denominator} rounded to the nearest value
     * of {@code format}, half to even, overflowing to infinity and underflowing to zero.
     * Both arguments are consumed.
     */
    private static long round(Natural numerator, Natural denominator, Format format) {
        // The value lies in [2^binaryExponent, 2^(binaryExponent + 1)).
        int binaryExponent = numerator.bitLength() - denominator.bitLength();
        Natural power = denominator.copy();
        Natural scaled = numerator.copy();
        if (binaryExponent >= 0) {
            power.shiftLeft(binaryExponent);
        } else {
            scaled.shiftLeft(-binaryExponent);
        }
        if (scaled.compareTo(power) < 0) {
            binaryExponent--;
        }
        // The exponent of the result's least significant bit.
        int quantum = Math.max(binaryExponent - format.precision + 1, format.minimumExponent);
        if (quantum >= 0) {
            denominator.shiftLeft(quantum);
        } else {
            numerator.shiftLeft(-quantum);
        }
        // significand = numerator / denominator, below 2^precision: one bit at a time from the top.
        Natural step = denominator.copy();
        step.shiftLeft(format.precision - 1);
        long significand = 0;
        for (int bit = format.precision - 1; bit >= 0; bit--) {
            if (numerator.compareTo(step) >= 0) {
                numerator.subtract(step);
                significand |= 1L << bit;
            }
            step.shiftRightOne();
        }
        int half = numerator.copy().setTwice(numerator).compareTo(denominator);
        if (half > 0 || (half == 0 && (significand & 1) == 1)) {
            significand++;
            if (significand == 1L << format.precision) {
                significand >>= 1;
                quantum++;
            }
        }
        if (quantum + format.precision - 1 > format.maximumExponent) {
            return format.infinity;
        }
        // A subnormal keeps a zero exponent field, and a significand that rounded up to the
        // leading bit carries into the smallest normal exponent: the sum lays out both.
        long biasedLessOne = quantum - format.minimumExponent;
        return (biasedLessOne << (format.precision - 1)) + significand;
    }

    private static NumberFormatException invalid(String text) {
        return new NumberFormatException("For input string: \"" + text + "\"");
    }

    // ---------------------------------------------------------------------------------------
    // Exact integers

    /**
     * A non-negative integer of any size, little-endian in 27 bit limbs, mutable so that the
     * digit loops allocate little. A limb times 10 plus a carry fits an int, and a limb times a
     * factor up to 2^26 fits a double exactly, which multiplies by wider factors without long.
     */
    private static final class Natural {
        private static final int BITS = 27;
        private static final int MASK = (1 << BITS) - 1;
        private static final double BASE = 1 << BITS;
        private static final int[] POWERS_OF_TEN = {1, 10, 100, 1000, 10000, 100000, 1000000,
                10000000};

        private int[] limbs;
        /** Limbs in use; the top one is non-zero unless the value is zero. */
        private int length;

        private Natural(int capacity) {
            limbs = new int[Math.max(capacity, 4)];
        }

        /** The unsigned 64 bit value {@code high * 2^32 + low}. */
        static Natural of(int high, int low) {
            return of(high, low, 4);
        }

        /** The unsigned 64 bit value {@code high * 2^32 + low}, with room for this many limbs. */
        static Natural of(int high, int low, int capacity) {
            Natural value = new Natural(capacity);
            value.limbs[0] = low & MASK;
            value.limbs[1] = ((low >>> BITS) | (high << (32 - BITS))) & MASK;
            value.limbs[2] = high >>> (2 * BITS - 32);
            value.length = 3;
            value.trim();
            return value;
        }

        /** A run of decimal or hexadecimal digits. */
        static Natural ofDigits(CharSequence digits, int radix) {
            Natural value = new Natural(digits.length() / 6 + 2);
            int chunkLength = radix == 10 ? 7 : 6;
            int count = digits.length();
            int index = 0;
            while (index < count) {
                int chunk = Math.min(chunkLength, count - index);
                int part = 0;
                for (int end = index + chunk; index < end; index++) {
                    part = part * radix + hexDigit(digits.charAt(index));
                }
                if (radix == 10) {
                    value.multiply(POWERS_OF_TEN[chunk]);
                } else {
                    value.shiftLeft(4 * chunk);
                }
                value.add(part);
            }
            return value;
        }

        /** Makes this {@code a + b}, reusing its storage. */
        Natural setSum(Natural a, Natural b) {
            set(a);
            return add(b);
        }

        /** Makes this {@code 2 * a}, reusing its storage. */
        Natural setTwice(Natural a) {
            set(a);
            shiftLeft(1);
            return this;
        }

        // Plain loops rather than System.arraycopy, which under TeaVM allocates a view of the
        // array on every call.
        private void set(Natural other) {
            if (limbs.length < other.length + 1) {
                limbs = new int[other.length + 2];
            }
            for (int i = 0; i < other.length; i++) {
                limbs[i] = other.limbs[i];
            }
            length = other.length;
        }

        Natural copy() {
            Natural copy = new Natural(limbs.length);
            copy.set(this);
            return copy;
        }

        boolean isZero() {
            return length == 0;
        }

        int bitLength() {
            if (length == 0) {
                return 0;
            }
            return (length - 1) * BITS + 32 - Integer.numberOfLeadingZeros(limbs[length - 1]);
        }

        private void trim() {
            while (length > 0 && limbs[length - 1] == 0) {
                length--;
            }
        }

        private void reserve(int capacity) {
            if (limbs.length < capacity) {
                int[] grown = new int[Math.max(capacity, limbs.length * 2)];
                for (int i = 0; i < length; i++) {
                    grown[i] = limbs[i];
                }
                limbs = grown;
            }
        }

        void shiftLeft(int bits) {
            if (length == 0 || bits == 0) {
                return;
            }
            int whole = bits / BITS;
            int part = bits % BITS;
            reserve(length + whole + 1);
            if (part == 0) {
                for (int i = length - 1; i >= 0; i--) {
                    limbs[i + whole] = limbs[i];
                }
                length += whole;
            } else {
                limbs[length + whole] = 0;
                for (int i = length - 1; i >= 0; i--) {
                    int limb = limbs[i];
                    limbs[i + whole + 1] |= limb >>> (BITS - part);
                    limbs[i + whole] = (limb << part) & MASK;
                }
                length += whole + 1;
            }
            for (int i = 0; i < whole; i++) {
                limbs[i] = 0;
            }
            trim();
        }

        void shiftRightOne() {
            for (int i = 0; i < length; i++) {
                int carried = i + 1 < length ? (limbs[i + 1] & 1) << (BITS - 1) : 0;
                limbs[i] = (limbs[i] >>> 1) | carried;
            }
            trim();
        }

        /** Multiplies by a factor from 1 to 2^26. */
        Natural multiply(int factor) {
            double carry = 0;
            for (int i = 0; i < length; i++) {
                double product = (double) limbs[i] * factor + carry;
                double high = Math.floor(product / BASE);
                limbs[i] = (int) (product - high * BASE);
                carry = high;
            }
            while (carry > 0) {
                reserve(length + 1);
                double high = Math.floor(carry / BASE);
                limbs[length++] = (int) (carry - high * BASE);
                carry = high;
            }
            return this;
        }

        void multiplyPow10(int exponent) {
            for (; exponent >= 7; exponent -= 7) {
                multiply(10000000);
            }
            if (exponent > 0) {
                multiply(POWERS_OF_TEN[exponent]);
            }
        }

        /** Adds a value from 0 to 2^27 - 1. */
        void add(int addend) {
            int carry = addend;
            for (int i = 0; carry != 0; i++) {
                if (i == length) {
                    reserve(length + 1);
                    limbs[length++] = 0;
                }
                int sum = limbs[i] + carry;
                limbs[i] = sum & MASK;
                carry = sum >>> BITS;
            }
        }

        Natural add(Natural other) {
            reserve(Math.max(length, other.length) + 1);
            int carry = 0;
            for (int i = 0; i < other.length || carry != 0; i++) {
                if (i == length) {
                    limbs[length++] = 0;
                }
                int sum = limbs[i] + (i < other.length ? other.limbs[i] : 0) + carry;
                limbs[i] = sum & MASK;
                carry = sum >>> BITS;
            }
            return this;
        }

        /** Subtracts a value no greater than this one. */
        void subtract(Natural other) {
            int borrow = 0;
            for (int i = 0; i < length && (i < other.length || borrow != 0); i++) {
                int difference = limbs[i] - (i < other.length ? other.limbs[i] : 0) - borrow;
                borrow = difference < 0 ? 1 : 0;
                limbs[i] = difference & MASK;
            }
            trim();
        }

        int compareTo(Natural other) {
            if (length != other.length) {
                return length < other.length ? -1 : 1;
            }
            for (int i = length - 1; i >= 0; i--) {
                if (limbs[i] != other.limbs[i]) {
                    return limbs[i] < other.limbs[i] ? -1 : 1;
                }
            }
            return 0;
        }
    }
}
