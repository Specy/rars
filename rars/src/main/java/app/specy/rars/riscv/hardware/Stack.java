package app.specy.rars.riscv.hardware;

import java.util.ArrayList;
import java.util.List;

public class Stack {


    static List<StackFrame> callStack = new ArrayList<>();

    public static void pushCallStack(int pc, int ra, int sp, int fp, int[] registers) {
        callStack.add(new StackFrame(pc, ra, sp, fp, registers));
    }


    public static void pushCallStack(StackFrame frame) {
        callStack.add(frame);
    }

    public static StackFrame popCallStack() {
        if (callStack.isEmpty()) {
            return null;
        }
        return callStack.remove(callStack.size() - 1);
    }

    public static void popUntilIncluding(int pc){
        boolean hasPc = false;
        for(StackFrame frame : callStack){
            if(frame.getPC() == pc){
                hasPc = true;
                break;
            }
        }
        if(!hasPc){
            return;
        }
        while (!callStack.isEmpty() && callStack.get(callStack.size() - 1).getPC() != pc){
            callStack.remove(callStack.size() - 1);
        }
        if(!callStack.isEmpty() && callStack.get(callStack.size() - 1).getPC() == pc){
            callStack.remove(callStack.size() - 1);
        }
    }


    public static void clearCallStack() {
        callStack.clear();
    }

    public static List<StackFrame> getCallStack() {
        return callStack;
    }
}
