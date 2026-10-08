package app.specy.rars.riscv.syscalls;
import app.specy.rars.*;
import app.specy.rars.riscv.AbstractSyscall;
import app.specy.rars.riscv.hardware.*;
/** Runtime extension: recoverable allocation failure; service 9 retains reference semantics. */
public class SyscallRuntimeSbrk extends AbstractSyscall {
    public SyscallRuntimeSbrk() { super("RuntimeSbrk"); }
    public void simulate(ProgramStatement statement) {
        int address;
        try { address = Globals.memory.allocateBytesFromHeap(RegisterFile.getValue(10)); }
        catch (IllegalArgumentException failure) { address = -1; }
        RegisterFile.updateRegister(10, address);
    }
}
