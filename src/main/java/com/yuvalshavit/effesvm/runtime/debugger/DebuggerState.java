package com.yuvalshavit.effesvm.runtime.debugger;

import java.util.function.Function;

import com.yuvalshavit.effesvm.runtime.EffesState;

public class DebuggerState {
  private volatile RunState runState = RunState.RUNNING;
  private volatile EffesState effesState;
  private volatile int stepsCompleted;
  private EffesState.FrameInfo stepOverToFp;

  public void suspend() {
    synchronized (this) {
      runState = RunState.STEPPING; // so that it'll suspend next time through
      stepOverToFp = null; // so that it won't step over
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
      while (runState != RunState.SUSPENDED) {
        wait();
      }
      return reader.apply(effesState);
    }
  }

  public void resume() {
    synchronized (this) {
      runState = RunState.RUNNING;
      notifyAll();
    }
  }

  public void step() throws InterruptedException {
    stepInternal(false);
  }

  public void stepOver() throws InterruptedException {
    stepInternal(true);
  }

  private void stepInternal(boolean saveFp) throws InterruptedException {
    synchronized (this) {
      while (runState != RunState.SUSPENDED) {
        wait();
      }
      stepOverToFp = saveFp
        ? effesState.fp()
        : null;
      runState = RunState.STEPPING;
      notifyAll();
    }
  }

  public int getStepsCompleted() {
    return stepsCompleted;
  }

  public boolean isSuspended() {
    RunState localState = this.runState;
    return localState == RunState.SUSPENDED || localState == RunState.STEPPING;
  }

  void beforeAction(EffesState state) throws InterruptedException {
    synchronized (this) {
      if (runState == RunState.STEPPING) {
        if (stepOverToFp == null || stepOverToFp.equals(state.fp())) {
          stepOverToFp = null;
          runState = RunState.SUSPENDED;
          notifyAll();
        }
      }
      synchronized (this) {
        while (runState == RunState.SUSPENDED) {
          effesState = state;
          wait();
        }
        effesState = null;
      }
      ++stepsCompleted;
    }
  }

  public void awaitSuspension() throws InterruptedException {
    synchronized (this) {
      while (runState != RunState.SUSPENDED) {
        wait();
      }
    }
  }

  private enum RunState {
    RUNNING,
    SUSPENDED,
    STEPPING,
  }
}
