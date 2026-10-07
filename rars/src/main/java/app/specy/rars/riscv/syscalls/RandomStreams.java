package app.specy.rars.riscv.syscalls;

import app.specy.rars.Globals;
import app.specy.rars.util.JavaRandom;
import app.specy.rars.util.SystemIO;

import java.util.HashMap;

/*
Copyright (c) 2003-2008,  Pete Sanderson and Kenneth Vollmar

Developed by Pete Sanderson (psanderson@otterbein.edu)
and Kenneth Vollmar (kenvollmar@missouristate.edu)

Permission is hereby granted, free of charge, to any person obtaining 
a copy of this software and associated documentation files (the 
"Software"), to deal in the Software without restriction, including 
without limitation the rights to use, copy, modify, merge, publish, 
distribute, sublicense, and/or sell copies of the Software, and to 
permit persons to whom the Software is furnished to do so, subject 
to the following conditions:

The above copyright notice and this permission notice shall be 
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, 
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF 
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR 
ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF 
CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION 
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

(MIT license, http://www.opensource.org/licenses/mit-license.html)
 */

/**
 * The pseudorandom number generators the random services (40 to 44) draw from: one
 * {@code java.util.Random} per number the program names in a0, as RARS keeps them.
 *
 * <p>A generator the program has not seeded with service 40 starts, on its first use, from the seed
 * the IO environment supplies ({@code RISCVIO.randomSeed}): host randomness, as in RARS, unless the
 * host hands out a fixed seed so that a scripted run gets the same numbers every time.
 *
 * <p>The generators belong to the run. {@link #reset()} forgets them, which {@code initialize}
 * does, and every service that advances or reseeds one records its previous state in the history
 * first, so that undoing the service puts the generator back and stepping it again draws the same
 * number.
 */
public final class RandomStreams {
    /** The high half a recorded state has when the generator did not exist yet. */
    public static final int ABSENT = -1;

    private static final HashMap<Integer, JavaRandom> streams = new HashMap<>();

    private RandomStreams() {
    }

    /** Forgets every generator: the next use of each starts it from a new seed. */
    public static void reset() {
        streams.clear();
    }

    /**
     * The generator {@code index}, which a service is about to draw from: started from the
     * environment's seed if this is its first use, and its state recorded in the history.
     */
    static JavaRandom forDraw(int index) {
        JavaRandom stream = streams.get(index);
        if (stream == null) {
            stream = JavaRandom.fromSeed(SystemIO.randomSeed(index));
            streams.put(index, stream);
        }
        record(index, stream);
        return stream;
    }

    /**
     * Service 40: seeds generator {@code index} as {@code new Random(seed)} does, the int widened to
     * a long with its sign, creating the generator if it does not exist.
     */
    static void setSeed(int index, int seed) {
        JavaRandom stream = streams.get(index);
        record(index, stream);
        if (stream == null) {
            streams.put(index, JavaRandom.fromSeed(seed));
        } else {
            stream.setSeed(seed);
        }
    }

    private static void record(int index, JavaRandom stream) {
        if (Globals.getSettings().getBackSteppingEnabled()) {
            Globals.program.getBackStepper().addRandomStreamRestore(index,
                    stream == null ? ABSENT : stream.stateHigh(), stream == null ? 0 : stream.stateLow());
        }
    }

    /**
     * Undo: puts generator {@code index} back in a state the history recorded, or forgets it when
     * it did not exist then ({@code high} is {@link #ABSENT}).
     */
    public static void restore(int index, int high, int low) {
        if (high == ABSENT) {
            streams.remove(index);
            return;
        }
        JavaRandom stream = streams.get(index);
        if (stream == null) {
            streams.put(index, JavaRandom.fromState(high, low));
        } else {
            stream.setState(high, low);
        }
    }
}
