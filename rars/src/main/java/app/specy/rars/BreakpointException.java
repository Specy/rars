package app.specy.rars;

/**
 * This exception is only used to trigger breakpoints for ebreak.
 * <p>
 * Its a bit of a hack, but it works and somewhat makes logical sense.
 */
public class BreakpointException extends SimulationException {

    public String toString(){
        return "Breakpoint reached";
    }
}
