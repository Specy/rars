package app.specy.rars.util;

/**
 * {@code java.util.Random} of Java 21, exactly: the 48 bit linear congruential generator its
 * specification fixes, and the algorithms it draws {@code nextInt()}, {@code nextInt(bound)},
 * {@code nextFloat()} and {@code nextDouble()} from it with. RARS's random services draw from
 * {@code java.util.Random}, so a program that seeds a generator prints the numbers RARS prints.
 * TeaVM's own Random cannot stand in for it: its {@code nextInt(bound)} skips Java's rejection
 * loop, so a bounded draw differs.
 *
 * <p>The state is held as two 24 bit halves and advanced in double arithmetic, which holds every
 * product of a half and the multiplier exactly: TeaVM compiles a Java long into a BigInt, which
 * would cost far more than the rest of a draw.
 */
public final class JavaRandom {
    /** The two halves of Java's multiplier 0x5DEECE66D. */
    private static final int MULTIPLIER_HIGH = 0x5DE;
    private static final int MULTIPLIER_LOW = 0xECE66D;
    private static final int ADDEND = 0xB;
    private static final int HALF_MASK = 0xFFFFFF;
    private static final double TWO_24 = 16777216.0;
    /** One more than the largest seed {@link #fromSeed(double)} takes: the state has 48 bits. */
    public static final double SEED_LIMIT = 281474976710656.0;
    private static final double DOUBLE_UNIT = 1.0 / 9007199254740992.0; // 2^-53

    /** Bits 24 to 47 of the state. */
    private int high;
    /** Bits 0 to 23 of the state. */
    private int low;

    private JavaRandom(int high, int low) {
        this.high = high;
        this.low = low;
    }

    /** {@code new Random(seed)} for an int seed, which Java widens to a long with its sign. */
    public static JavaRandom fromSeed(int seed) {
        JavaRandom random = new JavaRandom(0, 0);
        random.setSeed(seed);
        return random;
    }

    /**
     * {@code new Random(seed)} for a seed from 0 to 2^48 - 1, whole: every seed a long can hold
     * scrambles to the state one of these does, since Java keeps only the low 48 bits.
     *
     * @throws IllegalArgumentException if the seed is not a whole number in that range.
     */
    public static JavaRandom fromSeed(double seed) {
        if (!(seed >= 0 && seed < SEED_LIMIT) || seed != Math.floor(seed)) {
            throw new IllegalArgumentException("A seed must be a whole number from 0 to 2^48 - 1");
        }
        int high = (int) Math.floor(seed / TWO_24);
        int low = (int) (seed - high * TWO_24);
        return new JavaRandom(high ^ MULTIPLIER_HIGH, low ^ MULTIPLIER_LOW);
    }

    /** A generator in a state {@link #stateHigh()} and {@link #stateLow()} reported. */
    public static JavaRandom fromState(int high, int low) {
        return new JavaRandom(high & HALF_MASK, low & HALF_MASK);
    }

    /** {@code setSeed(seed)} for an int seed, widened to a long with its sign as Java does. */
    public void setSeed(int seed) {
        // Java scrambles (seed ^ multiplier) & (2^48 - 1); bits 24 to 47 of the sign-extended
        // seed are bits 24 to 31 of the int, then copies of its sign.
        high = ((seed >> 24) & HALF_MASK) ^ MULTIPLIER_HIGH;
        low = (seed & HALF_MASK) ^ MULTIPLIER_LOW;
    }

    /** Bits 24 to 47 of the state, which with {@link #stateLow()} is all of it. */
    public int stateHigh() {
        return high;
    }

    /** Bits 0 to 23 of the state. */
    public int stateLow() {
        return low;
    }

    /** Puts back a state {@link #stateHigh()} and {@link #stateLow()} reported. */
    public void setState(int high, int low) {
        this.high = high & HALF_MASK;
        this.low = low & HALF_MASK;
    }

    /**
     * {@code next(bits)}: advances the state to {@code state * 0x5DEECE66D + 0xB} modulo 2^48 and
     * returns its top {@code bits} bits, for {@code bits} from 24 to 32.
     */
    private int next(int bits) {
        // (h * 2^24 + l) * (mh * 2^24 + ml) is l * ml + (h * ml + l * mh) * 2^24 modulo 2^48, the
        // h * mh term being a multiple of 2^48. Each product is below 2^48 and each sum below
        // 2^50, so every step is exact in a double.
        double lowSum = (double) low * MULTIPLIER_LOW + ADDEND;
        double carry = Math.floor(lowSum / TWO_24);
        double highSum = (double) high * MULTIPLIER_LOW + (double) low * MULTIPLIER_HIGH + carry;
        low = (int) (lowSum - carry * TWO_24);
        high = (int) (highSum % TWO_24);
        // (int) (state >>> (48 - bits)), the top bits landing in an int as Java's cast leaves them.
        return (high << (bits - 24)) | (low >>> (48 - bits));
    }

    /** {@code nextInt()}. */
    public int nextInt() {
        return next(32);
    }

    /**
     * {@code nextInt(bound)}, with Java's rejection of the candidates that would make the low
     * values more likely.
     *
     * @throws IllegalArgumentException if {@code bound} is not positive, before drawing.
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        int r = next(31);
        int m = bound - 1;
        if ((bound & m) == 0) {
            // (int) ((bound * (long) r) >> 31): bound is 2^k, so that is r shifted right by 31 - k.
            r = r >> (31 - Integer.numberOfTrailingZeros(bound));
        } else {
            for (int u = r; u - (r = u % bound) + m < 0; u = next(31)) {
                // a candidate from the incomplete last span of bound values: draw again
            }
        }
        return r;
    }

    /** {@code nextFloat()}: 24 random bits over 2^24. */
    public float nextFloat() {
        return next(24) / ((float) (1 << 24));
    }

    /** {@code nextDouble()}: 53 random bits, 26 then 27, over 2^53. */
    public double nextDouble() {
        double upper = next(26);
        double lower = next(27);
        return (upper * 134217728.0 + lower) * DOUBLE_UNIT;
    }
}
