package app.specy.rars.riscv.hardware;

/**
 * Tracks the load-reserved / store-conditional reservation for the A extension.
 * <p>
 * RARS simulates a single hart, so there is no other agent that can steal a
 * reservation. What this table still models faithfully is the part students get
 * wrong: a store-conditional only succeeds when it is paired with a
 * load-reserved on the same address, so <code>sc.w</code> must always have its
 * result checked. A reservation is placed by <code>lr.w</code>/<code>lr.d</code>
 * and cleared by any store-conditional or atomic memory operation.
 * <p>
 * The reservation is not tracked by the back stepper, so undoing past an
 * <code>lr</code> leaves it cleared.
 */
public class ReservationTable {
    private static int address = 0;
    private static boolean valid = false;

    /**
     * Reserve an address, replacing any reservation already held.
     */
    public static void reserve(int addr) {
        address = addr;
        valid = true;
    }

    /**
     * @return true if a reservation is currently held on exactly this address.
     */
    public static boolean isReserved(int addr) {
        return valid && address == addr;
    }

    /**
     * Drop any reservation held on this address, leaving others untouched.
     */
    public static void invalidate(int addr) {
        if (valid && address == addr) {
            reset();
        }
    }

    /**
     * Drop any reservation. Called when a program is (re)initialized.
     */
    public static void reset() {
        address = 0;
        valid = false;
    }
}
