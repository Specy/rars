package app.specy.rars.riscv.syscalls;

import app.specy.rars.ProgramStatement;
import app.specy.rars.riscv.AbstractSyscall;
import app.specy.rars.riscv.hardware.RegisterFile;
import app.specy.rars.util.SystemIO;

/*
Copyright (c) 2003-2009,  Pete Sanderson and Kenneth Vollmar

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
 * Service to play a simulated MIDI tone, without waiting for it to end.
 */

public class SyscallMidiOut extends AbstractSyscall {
    // Endpoints of ranges for the three "byte" parameters.  The duration
    // parameter is limited at the high end only by the int range.
    static final int rangeLowEnd = 0;
    static final int rangeHighEnd = 127;

    // What a parameter outside its range becomes, RARS's ToneGenerator defaults.
    static final int DEFAULT_PITCH = 60; // middle C
    static final int DEFAULT_DURATION = 1000; // ms
    static final int DEFAULT_INSTRUMENT = 0; // acoustic grand piano
    static final int DEFAULT_VOLUME = 100;

    /** The arguments both MIDI services take, as their documentation lists them. */
    static final String INPUTS = "a0 = pitch (note), from 0 to 127, 60 being middle C<br>"
            + "a1 = duration in milliseconds<br>"
            + "a2 = instrument, from 0 to 127, 0 being acoustic grand piano<br>"
            + "a3 = volume, from 0 to 127<br>"
            + "A value outside its range becomes the default: a0=60, a1=1000, a2=0, a3=100";

    public SyscallMidiOut() {
        super("MidiOut", "Outputs simulated MIDI tone to sound card (does not wait for sound to end).",
                INPUTS, "N/A");
    }

    /**
     * Performs syscall function to send MIDI output to the sound card. The sound itself belongs to
     * the IO environment, which returns without waiting for it to end.
     */
    public void simulate(ProgramStatement statement) {
        play();
    }

    /**
     * Reads the tone from a0 to a3, puts each parameter outside its range back to its default and
     * hands the tone to the IO environment. MidiOutSync (33) shares it.
     *
     * @return the duration played, in milliseconds
     */
    static int play() {
        int pitch = RegisterFile.getValue("a0");
        int duration = RegisterFile.getValue("a1");
        int instrument = RegisterFile.getValue("a2");
        int volume = RegisterFile.getValue("a3");
        if (pitch < rangeLowEnd || pitch > rangeHighEnd) pitch = DEFAULT_PITCH;
        if (duration < 0) duration = DEFAULT_DURATION;
        if (instrument < rangeLowEnd || instrument > rangeHighEnd) instrument = DEFAULT_INSTRUMENT;
        if (volume < rangeLowEnd || volume > rangeHighEnd) volume = DEFAULT_VOLUME;
        SystemIO.midiOut(pitch, duration, instrument, volume);
        return duration;
    }

}
