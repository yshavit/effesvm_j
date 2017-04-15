package com.yuvalshavit.effesvm.runtime.debugger;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.runtime.DebugServerContext;
import com.yuvalshavit.effesvm.runtime.EffesState;

public class DebuggerState {
  /**
   * First key is a module id, second key is a function id. The BitSet is such that it has a True bit at the index one past its last op; that is,
   * bitset.length() is the number of ops in the function.
   */
  private final Map<String,Map<String,BitSet>> modulesToFunctionsToOpBreakPoints = new HashMap<>();
  private static final Predicate<EffesState> always = e -> true;
  private static final Predicate<EffesState> never = e -> false;
  private volatile int stepsCompleted;
  private volatile boolean running = true;
  private Predicate<EffesState> suspendBeforeNextAction = never;
  private EffesState effesState;

  public DebuggerState(DebugServerContext context) {
    context.modules().forEach((moduleId, moduleData) -> {
      Map<String,BitSet> functionsMap = new HashMap<>();
      modulesToFunctionsToOpBreakPoints.put(moduleId.toString(), functionsMap);
      moduleData.functions().forEach(function -> {
        BitSet bs = new BitSet(function.nOps() + 1);
        bs.set(function.nOps());
        functionsMap.put(function.id().toString(), bs);
      });
    });
  }

  public void suspend() {
    synchronized (this) {
      suspendBeforeNextAction = always; // so that it'll suspend next time through
      notifyAll();
    }
  }

  /**
   * Waits for the debugger to go into suspend mode, and then reads the EffesState while holding onto a lock. Since the lock prohibits other DebuggerState
   * actions (including resuming the VM or responding to other requests), the consumer action should be fast.
   *
   * All actions on the state happen-before it is passed to the consumer, and any modifications the consumer makes happen-before anyone else reads the state.
   */
  public <R> R visitStateUnderLock(Function<? super EffesState, ? extends R> reader) throws InterruptedException {
    synchronized (this) {
      while (running) {
        wait();
      }
      return reader.apply(effesState);
    }
  }

  public void resume() {
    synchronized (this) {
      running = true;
      suspendBeforeNextAction = never;
      notifyAll();
    }
  }

  public void step() throws InterruptedException {
    stepInternal(current -> always);
  }

  public void stepOver() throws InterruptedException {
    stepInternal(current -> {
      int currentDepth = current.frameDepth();
      return state -> state.frameDepth() <= currentDepth;
    });
  }

  public void stepOut() throws InterruptedException {
    stepInternal(current -> {
      int currentDepth = current.frameDepth();
      return state -> state.frameDepth() < currentDepth;
    });
  }

  public void setBreakpoint(String moduleId, String functionId, int opIdx, boolean on) {
    useBitSet(moduleId, functionId, bs -> {
      if (opIdx < 0 || opIdx >= bs.length()) {
        throw new IndexOutOfBoundsException(String.format("%s%s[%d]", moduleId, functionId, opIdx));
      }
      bs.set(opIdx, on);
      return null;
    });
  }

  public BitSet getDebugPoints(String moduleIdStr, String functionId) {
    return useBitSet(moduleIdStr, functionId, bs -> (BitSet) bs.clone());
  }

  private <R> R useBitSet(String moduleId, String functionId, Function<? super BitSet, ? extends R> action) {
    Map<String,BitSet> functionsToBitset = modulesToFunctionsToOpBreakPoints.get(moduleId);
    if (functionsToBitset == null) {
      throw new IllegalArgumentException(String.format("no such function: %s %s", moduleId, functionId));
    }
    BitSet bitSet = functionsToBitset.get(functionId);
    if (bitSet == null) {
      throw new IllegalArgumentException(String.format("no such function: %s %s", moduleId, functionId));
    }
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (bitSet) {
      return action.apply(bitSet);
    }
  }

  private void stepInternal(Function<EffesState,Predicate<EffesState>> suspendBeforeNextActionFactory) throws InterruptedException {
    synchronized (this) {
      while (running) {
        wait();
      }
      this.suspendBeforeNextAction = suspendBeforeNextActionFactory.apply(effesState);
      running = true;
      notifyAll();
    }
  }

  public int getStepsCompleted() {
    return stepsCompleted;
  }

  public boolean isSuspended() {
    return ! running;
  }

  void beforeAction(EffesState state) throws InterruptedException {
    synchronized (this) {
      boolean shouldSuspend;
      if (suspendBeforeNextAction.test(state)) {
        shouldSuspend = true;
      } else {
        EffesFunction<Operation> currentFunction = state.pc().getCurrentFunction();
        String currentModuleId = currentFunction.moduleId().toString();
        String currentFunctionId = currentFunction.id().toString();
        int currentOpIdx = state.pc().getOpIdx();
        shouldSuspend = useBitSet(currentModuleId, currentFunctionId, bs -> bs.get(currentOpIdx));
      }
      if (shouldSuspend) {
        running = false;
        notifyAll();
      }
    }
    synchronized (this) {
      while (!running) {
        effesState = state;
        wait();
      }
      effesState = null;
      ++stepsCompleted;
    }
  }

  public void awaitSuspension() throws InterruptedException {
    synchronized (this) {
      while (running) {
        wait();
      }
    }
  }
}
