package app.specy.rars.riscv.hardware;

import app.specy.rars.SimulationException;
import app.specy.rars.riscv.Instruction;
import app.specy.rars.simulator.Simulator;

/**
 * Manages the flow of interrupts to the processor
 * <p>
 * Roughly corresponds to PLIC in the spec, but it additionally (kindof) handles
 */
// TODO: add backstepper support
public class InterruptController {
    // Lock for synchronizing as this is a static class.
    //
    // Kept as the identity other code locks on, but this fork is headless and compiled to
    // JavaScript by TeaVM, where the simulator and every IO handler that can raise an interrupt run
    // on one thread. The accessors below are therefore no longer synchronized: the simulator checks
    // all three pending flags on every instruction, and entering and leaving these monitors cost
    // more than the checks themselves.
    public static final Object lock = new Object();

    // Status for the interrupt state
    private static boolean externalPending = false;
    private static int externalValue;
    private static boolean timerPending = false;
    private static int timerValue;

    //Status for trap state
    private static boolean trapPending = false;
    private static SimulationException trapSE;
    private static int trapPC;

    public static void reset() {
        {
            externalPending = false;
            timerPending = false;
            trapPending = false;
        }
    }

    public static boolean registerExternalInterrupt(int value) {
        {
            if (externalPending) return false;
            externalValue = value;
            externalPending = true;
            Simulator.getInstance().interrupt();
            return true;
        }
    }

    public static boolean registerTimerInterrupt(int value) {
        {
            if (timerPending) return false;
            timerValue = value;
            timerPending = true;
            Simulator.getInstance().interrupt();
            return true;
        }
    }

    public static boolean registerSynchronousTrap(SimulationException se, int pc) {
        {
            if (trapPending) return false;
            trapSE = se;
            trapPC = pc;
            trapPending = true;
            return true;
        }
    }

    public static boolean externalPending() {
        {
            return externalPending;
        }
    }

    public static boolean timerPending() {
        {
            return timerPending;
        }
    }

    public static boolean trapPending() {
        {
            return trapPending;
        }
    }

    public static int claimExternal() {
        {
            assert externalPending : "Cannot claim, no external interrupt pending";
            externalPending = false;
            return externalValue;
        }
    }

    public static int claimTimer() {
        {
            assert timerPending : "Cannot claim, no timer interrupt pending";
            timerPending = false;
            return timerValue;
        }
    }

    public static SimulationException claimTrap() {
        {
            assert trapPending : "Cannot claim, no trap pending";
            assert trapPC == RegisterFile.getProgramCounter() - Instruction.INSTRUCTION_LENGTH : "trapPC doesn't match current pc";
            trapPending = false;
            return trapSE;
        }
    }
}
