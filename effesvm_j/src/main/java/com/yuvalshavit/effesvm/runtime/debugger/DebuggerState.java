package com.yuvalshavit.effesvm.runtime.debugger;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Predicate;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.ops.OpInfo;
import com.yuvalshavit.effesvm.runtime.DebugServerContext;
import com.yuvalshavit.effesvm.runtime.EffesState;

public class DebuggerState {
  /**
   * First key is a module id, second key is a function id. The BitSet is such that it has a True bit at the index one past its last op; that is,
   * bitset.length() is the number of ops in the function.
   */
  private final Map<EffesFunctionId,BitSet> functionIdToOpBreakPoints = new HashMap<>();
  private static final Predicate<EffesState> always = e -> true;
  private static final Predicate<EffesState> never = e -> false;
  private volatile int stepsCompleted;
  private volatile boolean running = true;
  private Predicate<EffesState> suspendBeforeNextAction = never;
  private EffesState effesState;

  public DebuggerState(DebugServerContext context) {
    context.modules().forEach((mid, module) -> module.functions().forEach(function -> {
      BitSet bs = new BitSet(function.nOps() + 1);
      bs.set(function.nOps());
      functionIdToOpBreakPoints.put(function.id(), bs);
    }));
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

  public void stepPastLine(StepPast which) throws InterruptedException {
    stepInternal(which.predcateFactory);
  }

  public void setBreakpoint(EffesFunctionId fid, int opIdx, boolean on) {
    useBitSet(fid, bs -> {
      if (opIdx < 0 || opIdx >= bs.length()) {
        throw new IndexOutOfBoundsException(fid.toString("@" + opIdx));
      }
      bs.set(opIdx, on);
      return null;
    });
  }

  public BitSet getDebugPoints(EffesFunctionId fid) {
    return useBitSet(fid, bs -> (BitSet) bs.clone());
  }

  private <R> R useBitSet(EffesFunctionId fid, Function<? super BitSet, ? extends R> action) {
    BitSet bitSet = functionIdToOpBreakPoints.get(fid);
    if (bitSet == null) {
      throw new NoSuchElementException(String.format("no such function: %s", fid));
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
        EffesFunction currentFunction = state.pc().getCurrentFunction();
        int currentOpIdx = state.pc().getOpIdx();
        shouldSuspend = useBitSet(currentFunction.id(), bs -> bs.get(currentOpIdx));
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

  public boolean awaitRunStateChanged() throws InterruptedException {
    boolean initial = running;
    synchronized (this) {
      while (running == initial) {
        wait();
      }
    }
    return ! initial;
  }

  public enum StepPast {
    SOURCE_LINE(orig -> {
      int origFrameDepth = orig.frameDepth();
      int origLineNumber = orig.pc().getOp().info().sourceLineNumber();
      return origLineNumber < 0
        ? always
        : state -> {
          int currentFrameDepth = state.frameDepth();
          return currentFrameDepth < origFrameDepth // popped up a frame
            || (currentFrameDepth == origFrameDepth && differentLine(state, origLineNumber)); // same frame, different line
        };
    }),
    SOURCE_COLUMN(orig -> {
      int origFrameDepth = orig.frameDepth();
      OpInfo origInfo = orig.pc().getOp().info();
      int origLineNumber = origInfo.sourceLineNumber();
      int origColumn = origInfo.sourcePositionInLine();
      return origLineNumber < 0
        ? always
        : (state -> state.frameDepth() != origFrameDepth || differentLine(state, origLineNumber) || differentPositionInLine(state, origColumn));
    }),
    ;

    private static boolean differentLine(EffesState current, int origLineNumber) {
      return current.pc().getOp().info().sourceLineNumber() != origLineNumber;
    }

    private static boolean differentPositionInLine(EffesState current, int origPositionInLine) {
      return current.pc().getOp().info().sourcePositionInLine() != origPositionInLine;
    }

    private final Function<EffesState,Predicate<EffesState>> predcateFactory;

    StepPast(Function<EffesState, Predicate<EffesState>> predcateFactory) {
      this.predcateFactory = predcateFactory;
    }
  }
}
