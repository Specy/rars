import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Writes the golden vectors that test/java-numbers.mjs, test/assembler-literals.mjs,
 * test/random.mjs and test/text.mjs check the Core against: how Java prints floats and doubles
 * (Float.toString, Double.toString), how it parses a line typed for read int, float and double
 * (Integer.parseInt, Float.parseFloat and Double.parseDouble of the trimmed line), what the
 * assembler's .float stores (a double rounded to a float, out of range beyond the largest float),
 * the numbers java.util.Random draws for the random services, and how Java decodes the bytes of a
 * string from UTF-8 (new String(bytes, UTF_8)) and encodes a string as UTF-8 (getBytes(UTF_8)),
 * malformed bytes and unpaired surrogates included, which is what MARS and RARS do on the JDK they
 * run on. Every value is chosen deterministically, so running it again writes the same file.
 *
 * Run it with JDK 21 from this directory:
 *
 *     java GoldenVectors.java > vectors.json
 */
public class GoldenVectors {
    private static final SplittableRandom random = new SplittableRandom(20261005);

    public static void main(String[] args) {
        String version = System.getProperty("java.version");
        if (!version.startsWith("21")) {
            throw new IllegalStateException("The vectors are Java 21's; this is Java " + version);
        }
        StringBuilder out = new StringBuilder();
        out.append("{\n  \"java\": ").append(quote(version)).append(",\n");
        section(out, "floatToString", floatsToFormat(), true);
        section(out, "doubleToString", doublesToFormat(), true);
        section(out, "parseInt", intLines(), true);
        section(out, "parseFloat", floatLines(), true);
        section(out, "parseDouble", doubleLines(), true);
        section(out, "floatDirective", floatDirectiveLines(), true);
        section(out, "utf8Decode", utf8Decodings(), true);
        section(out, "utf8Encode", utf8Encodings(), true);
        random(out);
        out.append("}\n");
        System.out.print(out);
    }

    private static void section(StringBuilder out, String name, List<String> vectors, boolean more) {
        out.append("  ").append(quote(name)).append(": [\n");
        for (int i = 0; i < vectors.size(); i++) {
            out.append("    ").append(vectors.get(i)).append(i + 1 < vectors.size() ? ",\n" : "\n");
        }
        out.append("  ]").append(more ? ",\n" : "\n");
    }

    // ---------------------------------------------------------------------------------------
    // Formatting: [bits as hex, Java's text]

    private static List<String> floatsToFormat() {
        Set<Integer> bits = new LinkedHashSet<>();
        int[] specials = {0, 0x80000000, 0x7f800000, 0xff800000, 0x7fc00000, 0x7f800001, 0xffffffff,
                1, 0x007fffff, 0x00800000, 0x7f7fffff};
        for (int value : specials) bits.add(value);
        for (int value = 2; value <= 300; value++) bits.add(value);
        for (int value = 0x007ffff0; value < 0x007fffff; value++) bits.add(value);
        for (int i = 0; i < 300; i++) bits.add(random.nextInt(0x007fffff) + 1);
        for (int exponent = -46; exponent <= 39; exponent++) {
            neighbours(bits, Float.parseFloat("1e" + exponent), 2);
        }
        for (int exponent = -149; exponent <= 127; exponent++) neighbours(bits, Math.scalb(1f, exponent), 1);
        neighbours(bits, 0.001f, 25);
        neighbours(bits, 1e7f, 25);
        neighbours(bits, 9999999f, 10);
        neighbours(bits, 1e-4f, 5);
        for (int value = 0; value <= 300; value++) bits.add(Float.floatToRawIntBits(value));
        for (int digits = 1; digits <= 9; digits++) {
            bits.add(Float.floatToRawIntBits((float) (Math.pow(10, digits) - 1)));
        }
        for (String text : COMMON) bits.add(Float.floatToRawIntBits(Float.parseFloat(text)));
        // Values whose shortest decimal has one digit but a two digit one lies closer.
        int found = 0;
        for (int value = 1; value < 0x00800000 && found < 150; value++) {
            if (closerTwoDigitFloat(value)) {
                bits.add(value);
                found++;
            }
        }
        // Ties: the value lies exactly halfway between the two shortest decimals. That needs a
        // value j * 2^(q-1), j odd, in a binade whose spacing is between 10^q and 2^(q-1).
        found = 0;
        for (int i = 0; i < 100000 && found < 80; i++) {
            int q = -1 - random.nextInt(5);
            int lowest = (int) Math.ceil(23 + q * Math.log(10) / Math.log(2));
            if (lowest > q + 22) continue;
            int binade = lowest + random.nextInt(q + 22 - lowest + 1);
            int bitsOfJ = binade - q + 1;
            int j = random.nextInt(1 << bitsOfJ) | (1 << bitsOfJ) | 1;
            float value = Math.scalb((float) j, q - 1);
            if (isTie(new BigDecimal(value), Float.toString(value))) {
                bits.add(Float.floatToRawIntBits(value));
                found++;
            }
        }
        for (int i = 0; i < 2000; i++) bits.add(random.nextInt());
        List<String> vectors = new ArrayList<>();
        for (int value : bits) {
            float f = Float.intBitsToFloat(value);
            if (!Float.isNaN(f) && random.nextInt(4) == 0) value |= 0x80000000;
            f = Float.intBitsToFloat(value);
            vectors.add("[" + quote(String.format("%08x", value)) + ", " + quote(Float.toString(f)) + "]");
        }
        return vectors;
    }

    private static List<String> doublesToFormat() {
        Set<Long> bits = new LinkedHashSet<>();
        long[] specials = {0L, 0x8000000000000000L, 0x7ff0000000000000L, 0xfff0000000000000L,
                0x7ff8000000000000L, 0x7ff0000000000001L, 0xffffffffffffffffL, 1L, 0x000fffffffffffffL,
                0x0010000000000000L, 0x7fefffffffffffffL};
        for (long value : specials) bits.add(value);
        for (long value = 2; value <= 300; value++) bits.add(value);
        for (long value = 0x000ffffffffffff0L; value < 0x000fffffffffffffL; value++) bits.add(value);
        for (int i = 0; i < 200; i++) bits.add(random.nextLong(0x000fffffffffffffL) + 1);
        for (int exponent = -324; exponent <= 308; exponent++) {
            neighbours(bits, Double.parseDouble("1e" + exponent), exponent % 4 == 0 ? 2 : 1);
        }
        for (int exponent = -1074; exponent <= 1023; exponent++) {
            if (exponent < -1050 || exponent > 1000 || (exponent > -30 && exponent < 60) || exponent % 17 == 0) {
                neighbours(bits, Math.scalb(1d, exponent), 1);
            }
        }
        neighbours(bits, 0.001, 25);
        neighbours(bits, 1e7, 25);
        neighbours(bits, 9999999.999999998, 10);
        neighbours(bits, 1e-4, 5);
        neighbours(bits, 0x1p53, 3);
        for (int value = 0; value <= 300; value++) bits.add(Double.doubleToRawLongBits(value));
        for (int digits = 1; digits <= 17; digits++) bits.add(Double.doubleToRawLongBits(Math.pow(10, digits) - 1));
        for (String text : COMMON) bits.add(Double.doubleToRawLongBits(Double.parseDouble(text)));
        // Values whose shortest decimal has one digit but a two digit one lies closer.
        int found = 0;
        for (long value = 1; value < 2000000 && found < 150; value++) {
            if (closerTwoDigitDouble(value)) {
                bits.add(value);
                found++;
            }
        }
        // Ties: the value lies exactly halfway between the two shortest decimals. That needs a
        // value j * 2^(q-1), j odd, in a binade whose spacing is between 10^q and 2^(q-1).
        found = 0;
        for (int i = 0; i < 100000 && found < 120; i++) {
            int q = -1 - random.nextInt(12);
            int lowest = (int) Math.ceil(52 + q * Math.log(10) / Math.log(2));
            int binade = lowest + random.nextInt(q + 51 - lowest + 1);
            int bitsOfJ = binade - q + 1;
            long j = random.nextLong(1L << bitsOfJ) | (1L << bitsOfJ) | 1;
            double value = Math.scalb((double) j, q - 1);
            if (isTie(new BigDecimal(value), Double.toString(value))) {
                bits.add(Double.doubleToRawLongBits(value));
                found++;
            }
        }
        for (int i = 0; i < 2000; i++) bits.add(random.nextLong());
        List<String> vectors = new ArrayList<>();
        for (long value : bits) {
            double d = Double.longBitsToDouble(value);
            if (!Double.isNaN(d) && random.nextInt(4) == 0) value |= 0x8000000000000000L;
            d = Double.longBitsToDouble(value);
            vectors.add("[" + quote(String.format("%016x", value)) + ", " + quote(Double.toString(d)) + "]");
        }
        return vectors;
    }

    private static final String[] COMMON = {"0.1", "0.2", "0.3", "0.7", "1.1", "2.5", "3.14159", "3.141592653589793",
            "2.718281828459045", "0.5", "0.25", "0.125", "1e-5", "1e-3", "1e-4", "123.456", "-42.5", "6.02214076e23",
            "1.602176634e-19", "100", "1000000", "12345678", "0.333333333333", "0.6666666666666666", "1e22", "1e23",
            "5e-324", "1.7976931348623157e308", "3.4028235e38", "1.4e-45", "65536", "4294967296", "0.015625"};

    private static void neighbours(Set<Integer> bits, float value, int distance) {
        int center = Float.floatToRawIntBits(value);
        for (int offset = -distance; offset <= distance; offset++) {
            int neighbour = center + offset;
            if (neighbour >= 0 && neighbour <= 0x7f800000) bits.add(neighbour);
        }
    }

    private static void neighbours(Set<Long> bits, double value, int distance) {
        long center = Double.doubleToRawLongBits(value);
        for (int offset = -distance; offset <= distance; offset++) {
            long neighbour = center + offset;
            if (neighbour >= 0 && neighbour <= 0x7ff0000000000000L) bits.add(neighbour);
        }
    }

    /** Whether Java prints two digits for a float whose shortest round trip decimal has one. */
    private static boolean closerTwoDigitFloat(int bits) {
        float value = Float.intBitsToFloat(bits);
        String text = Float.toString(value);
        if (significantDigits(text) != 2) return false;
        BigDecimal exact = new BigDecimal(value);
        for (BigDecimal candidate : oneDigitNeighbours(exact)) {
            if (Float.parseFloat(candidate.toString()) == value) return true;
        }
        return false;
    }

    private static boolean closerTwoDigitDouble(long bits) {
        double value = Double.longBitsToDouble(bits);
        String text = Double.toString(value);
        if (significantDigits(text) != 2) return false;
        BigDecimal exact = new BigDecimal(value);
        for (BigDecimal candidate : oneDigitNeighbours(exact)) {
            if (Double.parseDouble(candidate.toString()) == value) return true;
        }
        return false;
    }

    private static List<BigDecimal> oneDigitNeighbours(BigDecimal exact) {
        List<BigDecimal> candidates = new ArrayList<>();
        candidates.add(exact.round(new MathContext(1, RoundingMode.FLOOR)));
        candidates.add(exact.round(new MathContext(1, RoundingMode.CEILING)));
        return candidates;
    }

    private static int significantDigits(String javaText) {
        String mantissa = javaText.replace("-", "").split("E")[0].replace(".", "");
        mantissa = mantissa.replaceFirst("^0+", "").replaceFirst("0+$", "");
        return mantissa.length();
    }

    /** Whether the value lies exactly halfway between Java's decimal and its neighbour at that length. */
    private static boolean isTie(BigDecimal exact, String javaText) {
        BigDecimal printed = new BigDecimal(javaText);
        BigDecimal unit = BigDecimal.ONE.movePointLeft(printed.stripTrailingZeros().scale());
        return exact.subtract(printed).abs().multiply(BigDecimal.valueOf(2)).compareTo(unit) == 0;
    }

    // ---------------------------------------------------------------------------------------
    // Parsing: [the line typed, the value read or null when the syscall reports invalid input]

    private static final String[] GRAMMAR = {"", " ", "\t", "\n", "+", "-", ".", "+.", "-.", "1.", ".5", "-.5", "+.5",
            "1e", "1e+", "1e-", "1e5", "1E5", "1e+5", "1e-5", "1.5e5f", "1f", "1d", "1D", "1F", "1.f", ".5d", "1.5ef",
            "1fd", "1ff", "1e5x", "NaN", "+NaN", "-NaN", "nan", "NAN", "NaNf", "Infinity", "-Infinity", "+Infinity",
            "infinity", "Inf", "-Inf", "Infinityf", "1_000", "1,5", "1..2", "1.2.3", "--1", "+-1", "-+1", "1 2",
            " 1 ", "\t1\n", "1\n", " 1", "1 ", " 1", "١", "１", "\u0000 3 \u001f", "0", "-0",
            "+0", "0.0", "-0.0", "00001", "000.0001", "0.000", ".0", "0.", "-0e5", "0e999999999999", "-0.0e-999",
            "1e2147483647", "1e2147483648", "1e-2147483648", "1e-2147483649", "1e99999999999999999999",
            "0x", "0x1", "0x1p", "0x1p+", "0x1.p1", "0x.p1", "0xp1", "0x.8p1", "0x1.8p1", "0X1P1", "0x1P-1", "-0x1.8p1d",
            "+0x1p1f", "0x1p1F", "0x1p1D", "0x1p1x", "0x1g", "0xg", "0x1.8", "0x1p1.5", "00x1p1", "0x1p2147483648",
            "0x1p-2147483649", "0x0p99999999999", "0x0.0p0", "-0x0p0", "0x1p-1074", "0x1p-1075", "0x1.0p-1075",
            "0x1.8p-1075", "0x1p-149", "0x1p-150", "0x1.8p-150", "0x1.fffffffffffff8p1023", "0x1.fffffffffffff7p1023",
            "0x1.fffffep127", "0x1.ffffffp127", "0x1.fffffefp127", "0x1.0000000000000801p0",
            "0x1.00000000000008p0", "0x1.00000000000018p0", "0x1.000001p0", "0x1.0000010000001p0", "0x1.000003p0",
            "0xffffffffffffffffffffp0", "0x.0000000000000000000001p0", "12abc", "abc", "3.7", "1e3", "0x1F", "1e",
            "1.0e1.0", "e5", ".e5", "1e5.", "--", "1e--5", "1e+-5"};

    private static List<String> intLines() {
        List<String> vectors = new ArrayList<>();
        Set<String> lines = new LinkedHashSet<>();
        String[] cases = {"0", "1", "-1", "+1", "+0", "-0", "42", "007", " 12 ", "\t-5\n", "12\n", "2147483647",
                "-2147483648", "+2147483647", "2147483648", "-2147483649", "99999999999", "-99999999999",
                "000000000000000000002147483647", "", " ", "\n", "+", "-", "++1", "--1", "+-1", "1+", "1 2", "12abc",
                "abc", "3.7", "1e3", "0x1F", "1_000", "1,000", "١٢٣", "１２３",
                "1٢" + "3", "-٥", "²", "Ⅷ", "①", "¼", "٫", " 12", "12 ",
                " 12", "𝟎", "𐒠", "\u0000 7 \u001f", "−" + "5", "－5"};
        for (String line : cases) lines.add(line);
        for (int c = 0; c <= 0xffff; c++) {
            if (Character.digit((char) c, 10) >= 0) {
                lines.add(String.valueOf((char) c));
                lines.add("-1" + (char) c);
            }
        }
        for (int c = 0x80; c <= 0xffff; c += 97) {
            if (Character.digit((char) c, 10) < 0 && !Character.isSurrogate((char) c)) lines.add("1" + (char) c);
        }
        for (int i = 0; i < 200; i++) lines.add(Integer.toString(random.nextInt()));
        for (int i = 0; i < 50; i++) lines.add(Long.toString(random.nextLong()));
        for (String line : lines) {
            String expected;
            try {
                expected = Integer.toString(Integer.parseInt(line.trim()));
            } catch (NumberFormatException e) {
                expected = "null";
            }
            vectors.add("[" + quote(line) + ", " + expected + "]");
        }
        return vectors;
    }

    private static List<String> floatLines() {
        List<String> vectors = new ArrayList<>();
        for (String line : floatLineSet()) {
            String expected;
            try {
                expected = quote(String.format("%08x", Float.floatToRawIntBits(Float.parseFloat(line.trim()))));
            } catch (NumberFormatException e) {
                expected = "null";
            }
            vectors.add("[" + quote(line) + ", " + expected + "]");
        }
        return vectors;
    }

    private static Set<String> floatLineSet;

    /**
     * The lines typed for read float, built once: they draw from the shared random source, so a
     * second build would draw different lines and change every section written after it.
     */
    private static Set<String> floatLineSet() {
        if (floatLineSet != null) return floatLineSet;
        Set<String> lines = new LinkedHashSet<>();
        for (String line : GRAMMAR) lines.add(line);
        for (String text : COMMON) lines.add(text);
        for (String text : new String[]{"3.4028235e38", "3.4028236e38", "3.40282356779733661637539395458142568448e38",
                "3.40282356779733661637539395458142568447e38", "3.4028235677973366e38", "1.4e-45", "1.401298464324817e-45",
                "7.006492321624085e-46", "7.006492321624086e-46", "7.0064923216240854e-46", "1.1754942e-38",
                "1.17549435e-38", "1.1754943508222875e-38", "2.3509887e-38", "1e39", "1e-46", "1e-45", "0.00000000000000000000000000000000000000000000140129846432481707092372958328991613128026194187651577175706828388979108268586060148663818836212158203125"}) {
            lines.add(text);
        }
        for (int i = 0; i < 300; i++) {
            float value = Float.intBitsToFloat(random.nextInt(0x7f800000));
            lines.add(Float.toString(value));
            lines.add(Double.toString(value));
            if (i % 3 == 0) lines.add(Float.toHexString(value));
        }
        for (int i = 0; i < 250; i++) {
            float value = Float.intBitsToFloat(random.nextInt(0x7f7fffff));
            BigDecimal low = new BigDecimal(value);
            BigDecimal midpoint = low.add(new BigDecimal(Math.nextUp(value))).divide(BigDecimal.valueOf(2));
            lines.add(midpoint.toString());
            // Close enough to the midpoint that going through a double first would land on it.
            BigDecimal nudge = midpoint.multiply(new BigDecimal("1e-25"));
            lines.add(midpoint.add(nudge).toString());
            lines.add(midpoint.subtract(nudge).toString());
        }
        addRandomDecimals(lines, 300, 50);
        floatLineSet = lines;
        return lines;
    }

    private static List<String> doubleLines() {
        Set<String> lines = new LinkedHashSet<>();
        for (String line : GRAMMAR) lines.add(line);
        for (String text : COMMON) lines.add(text);
        for (String text : new String[]{"1.7976931348623157e308", "1.7976931348623158e308", "1.7976931348623159e308",
                "179769313486231580793728971405303415079934132710037826936173778980444968292764750946649017977587207096330286416692887910946555547851940402630657488671505820681908902000708383676273854845817711531764475730270069855571366959622842914819860834936475292719074168444365510704342711559699508093042880177904174497791.9999999999999999999999999999999999999999999999999999999999999999999999",
                "179769313486231580793728971405303415079934132710037826936173778980444968292764750946649017977587207096330286416692887910946555547851940402630657488671505820681908902000708383676273854845817711531764475730270069855571366959622842914819860834936475292719074168444365510704342711559699508093042880177904174497792",
                "4.9e-324", "2.4703282292062327e-324", "2.4703282292062328e-324", "2.47032822920623272088e-324",
                "2.2250738585072011e-308", "2.2250738585072012e-308", "2.2250738585072014e-308", "1e309", "1e-325",
                "8.41e21", "9007199254740993", "9007199254740992.5", "9007199254740993.0000000000000000001",
                "1.00000000000000011102230246251565404236316680908203125",
                "1.00000000000000011102230246251565404236316680908203124",
                "1.00000000000000011102230246251565404236316680908203126"}) {
            lines.add(text);
        }
        for (int i = 0; i < 300; i++) {
            double value = Double.longBitsToDouble(random.nextLong(0x7ff0000000000000L));
            lines.add(Double.toString(value));
            if (i % 3 == 0) lines.add(Double.toHexString(value));
            if (i % 5 == 0) lines.add(new BigDecimal(value).round(new MathContext(25)).toString());
        }
        for (int i = 0; i < 160; i++) {
            // Moderate exponents keep the exact midpoints short enough to read.
            double value = i % 4 == 0 ? Double.longBitsToDouble(random.nextLong(0x7fefffffffffffffL))
                    : Math.scalb(1 + random.nextDouble(), random.nextInt(200) - 100);
            BigDecimal midpoint = new BigDecimal(value).add(new BigDecimal(Math.nextUp(value))).divide(BigDecimal.valueOf(2));
            lines.add(midpoint.toString());
            BigDecimal nudge = midpoint.multiply(new BigDecimal("1e-40"));
            lines.add(midpoint.add(nudge).toString());
            lines.add(midpoint.subtract(nudge).toString());
        }
        // A midpoint whose deciding digit lies past the thousandth.
        BigDecimal tiny = new BigDecimal(Double.MIN_VALUE).multiply(new BigDecimal("2.5"));
        lines.add(tiny.toPlainString() + "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001");
        lines.add(tiny.toPlainString());
        addRandomDecimals(lines, 400, 340);
        List<String> vectors = new ArrayList<>();
        for (String line : lines) {
            String expected;
            try {
                expected = quote(String.format("%016x", Double.doubleToRawLongBits(Double.parseDouble(line.trim()))));
            } catch (NumberFormatException e) {
                expected = "null";
            }
            vectors.add("[" + quote(line) + ", " + expected + "]");
        }
        return vectors;
    }

    /**
     * What `.float` stores in a MARS or RARS program: the token read as a double, then rounded to
     * a float, and null when the double is out of the float range (beyond Float.MAX_VALUE, which
     * the assemblers reject as an out-of-range value) or not a number at all.
     */
    private static List<String> floatDirectiveLines() {
        List<String> vectors = new ArrayList<>();
        for (String line : floatLineSet()) {
            String expected;
            try {
                double value = Double.parseDouble(line.trim());
                expected = value < -Float.MAX_VALUE || value > Float.MAX_VALUE ? "null"
                        : quote(String.format("%08x", Float.floatToIntBits((float) value)));
            } catch (NumberFormatException e) {
                expected = "null";
            }
            vectors.add("[" + quote(line) + ", " + expected + "]");
        }
        return vectors;
    }

    // ---------------------------------------------------------------------------------------
    // java.util.Random: the sequences the random services draw, per seed and service

    /** Seeds that fit an int reach the Core through service 40 too; every seed through randomSeed. */
    private static final long[] RANDOM_SEEDS = {0, 42, -1, Integer.MAX_VALUE, Integer.MIN_VALUE,
            123456789012345L, (1L << 48) - 1};
    private static final int[] RANDOM_BOUNDS = {1, 2, 3, 6, 7, 10, 100, 1000, 65536, 1 << 30, (1 << 30) + 1,
            1500000000, Integer.MAX_VALUE};
    /** One generator drawn from by every service in turn, as {service, bound}. */
    private static final int[][] MIXED_DRAWS = {{41, 0}, {42, 6}, {43, 0}, {44, 0}, {42, (1 << 30) + 1},
            {42, 1 << 30}, {41, 0}, {42, Integer.MAX_VALUE}, {44, 0}, {43, 0}, {42, 1}, {42, 1500000000},
            {41, 0}, {44, 0}, {42, 7}, {43, 0}, {42, 65536}, {41, 0}, {42, 3}, {44, 0}};

    private static void random(StringBuilder out) {
        out.append("  \"random\": {\n");
        out.append("    \"seeds\": [");
        for (int i = 0; i < RANDOM_SEEDS.length; i++) {
            out.append(i > 0 ? ", " : "").append(quote(Long.toString(RANDOM_SEEDS[i])));
        }
        out.append("],\n    \"sequences\": [\n");
        List<String> sequences = new ArrayList<>();
        for (long seed : RANDOM_SEEDS) {
            sequences.add(sequence(seed, 41, 0, 50));
            for (int bound : RANDOM_BOUNDS) sequences.add(sequence(seed, 42, bound, 20));
            sequences.add(sequence(seed, 43, 0, 50));
            sequences.add(sequence(seed, 44, 0, 50));
            Random random = new Random(seed);
            StringBuilder draws = new StringBuilder();
            StringBuilder values = new StringBuilder();
            for (int i = 0; i < 2 * MIXED_DRAWS.length; i++) {
                int[] draw = MIXED_DRAWS[i % MIXED_DRAWS.length];
                if (i > 0) {
                    draws.append(", ");
                    values.append(", ");
                }
                draws.append("[").append(draw[0]).append(", ").append(draw[1]).append("]");
                values.append(draw(random, draw[0], draw[1]));
            }
            sequences.add("{\"seed\": " + quote(Long.toString(seed)) + ", \"draws\": [" + draws
                    + "], \"values\": [" + values + "]}");
        }
        for (int i = 0; i < sequences.size(); i++) {
            out.append("      ").append(sequences.get(i)).append(i + 1 < sequences.size() ? ",\n" : "\n");
        }
        out.append("    ]\n  }\n");
    }

    /** {@code count} draws of one service from {@code new Random(seed)}, as a JSON object. */
    private static String sequence(long seed, int service, int bound, int count) {
        Random random = new Random(seed);
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < count; i++) {
            values.append(i > 0 ? ", " : "").append(draw(random, service, bound));
        }
        return "{\"seed\": " + quote(Long.toString(seed)) + ", \"service\": " + service
                + (service == 42 ? ", \"bound\": " + bound : "") + ", \"values\": [" + values + "]}";
    }

    /** One draw as the service hands it over: an int, or a float's or a double's bits in hex. */
    private static String draw(Random random, int service, int bound) {
        switch (service) {
            case 41:
                return Integer.toString(random.nextInt());
            case 42:
                return Integer.toString(random.nextInt(bound));
            case 43:
                return quote(String.format("%08x", Float.floatToRawIntBits(random.nextFloat())));
            default:
                return quote(String.format("%016x", Double.doubleToRawLongBits(random.nextDouble())));
        }
    }

    private static void addRandomDecimals(Set<String> lines, int count, int exponentRange) {
        for (int i = 0; i < count; i++) {
            StringBuilder text = new StringBuilder();
            if (random.nextInt(4) == 0) text.append(random.nextBoolean() ? '-' : '+');
            int digits = 1 + random.nextInt(25);
            for (int k = 0; k < digits; k++) text.append((char) ('0' + random.nextInt(10)));
            if (random.nextBoolean()) text.insert(text.length() - random.nextInt(digits + 1), '.');
            if (random.nextBoolean()) {
                text.append(random.nextBoolean() ? 'e' : 'E').append(random.nextInt(2 * exponentRange) - exponentRange);
            }
            if (random.nextInt(8) == 0) text.append("fFdD".charAt(random.nextInt(4)));
            if (random.nextInt(8) == 0) text.insert(0, ' ').append('\n');
            lines.add(text.toString());
        }
    }

    // ---------------------------------------------------------------------------------------

    /** A JSON string, every character outside printable ASCII escaped. */
    // ---------------------------------------------------------------------------------------
    // UTF-8: [bytes as hex, Java's text] and [text, its bytes as hex]

    /** One byte of every kind a decoder tells apart, without 00, which ends a string in memory. */
    private static final int[] UTF8_BYTES = {0x01, 0x41, 0x7f, 0x80, 0x8f, 0x90, 0x9f, 0xa0, 0xbf, 0xc0,
            0xc1, 0xc2, 0xc3, 0xdf, 0xe0, 0xe1, 0xec, 0xed, 0xee, 0xef, 0xf0, 0xf1, 0xf3, 0xf4, 0xf5,
            0xf7, 0xf8, 0xff};

    private static List<String> utf8Decodings() {
        Set<String> sequences = new LinkedHashSet<>();
        for (int first : UTF8_BYTES) {
            sequences.add(hex(first));
            for (int second : UTF8_BYTES) sequences.add(hex(first, second));
        }
        for (int lead : new int[]{0xc2, 0xe0, 0xe1, 0xed, 0xef, 0xf0, 0xf1, 0xf4, 0xf5}) {
            for (int second : new int[]{0x41, 0x80, 0x9f, 0xa0, 0xbf, 0xc3}) {
                for (int third : new int[]{0x41, 0x80, 0xbf}) {
                    sequences.add(hex(lead, second, third));
                    if (lead >= 0xf0) {
                        sequences.add(hex(lead, second, third, 0x80));
                        sequences.add(hex(lead, second, third, 0x41));
                    }
                }
            }
        }
        for (String text : new String[]{"\u00e9\u20ac\ud83d\ude00", "a\u00e9b\u20acc\ud83d\ude00d", "\u0080",
                "\u07ff", "\u0800", "\uffff", "\ud800\udc00", "\udbff\udfff", "\u20ac\u20ac"}) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            sequences.add(hex(bytes));
            // Every way of cutting it short, as a string read past the end of a buffer is.
            for (int length = 1; length < bytes.length; length++) {
                byte[] cut = new byte[length];
                System.arraycopy(bytes, 0, cut, 0, length);
                sequences.add(hex(cut));
            }
        }
        SplittableRandom bytes = new SplittableRandom(20261006);
        for (int i = 0; i < 200; i++) {
            int[] sequence = new int[1 + bytes.nextInt(12)];
            for (int j = 0; j < sequence.length; j++) {
                sequence[j] = bytes.nextInt(3) == 0 ? UTF8_BYTES[bytes.nextInt(UTF8_BYTES.length)] : 1 + bytes.nextInt(255);
            }
            sequences.add(hex(sequence));
        }
        List<String> vectors = new ArrayList<>();
        for (String sequence : sequences) {
            byte[] value = new byte[sequence.length() / 2];
            for (int i = 0; i < value.length; i++) value[i] = (byte) Integer.parseInt(sequence.substring(2 * i, 2 * i + 2), 16);
            vectors.add("[" + quote(sequence) + ", " + quote(new String(value, StandardCharsets.UTF_8)) + "]");
        }
        return vectors;
    }

    private static List<String> utf8Encodings() {
        String[] texts = {"A", "\u00e9", "\u20ac", "\ud83d\ude00", "\u00e9\u20ac\ud83d\ude00", "\u0080", "\u07ff",
                "\u0800", "\uffff", "\ud800\udc00", "\udbff\udfff", "\ud83d", "\ude00", "\ude00\ud83d",
                "\ud83d\ud83d\ude00", "a\ud83dz", "\ud83d\u00e9", "x\udfff", "\ud800", "\udbff\ud83d\ude00\udc00"};
        List<String> vectors = new ArrayList<>();
        for (String text : texts) vectors.add("[" + quote(text) + ", " + quote(hex(text.getBytes(StandardCharsets.UTF_8))) + "]");
        return vectors;
    }

    private static String hex(int... values) {
        StringBuilder out = new StringBuilder();
        for (int value : values) out.append(String.format("%02x", value & 0xff));
        return out.toString();
    }

    private static String hex(byte[] values) {
        StringBuilder out = new StringBuilder();
        for (byte value : values) out.append(String.format("%02x", value & 0xff));
        return out.toString();
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20 || c > 0x7e) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
