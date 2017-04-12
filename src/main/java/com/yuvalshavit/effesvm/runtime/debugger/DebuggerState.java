package com.yuvalshavit.effesvm.runtime.debugger;

import java.util.function.Function;

import com.yuvalshavit.effesvm.runtime.EffesState;

public class DebuggerState {
  private volatile RunState runState = RunState.RUNNING;
  private volatile EffesState effesState;
  private volatile int stepsCompleted;

  public void suspend() {
    synchronized (this) {
      runState = RunState.SUSPENDED;
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
      effesState = null;
      runState = RunState.RUNNING;
      notifyAll();
    }
  }

  public void step() {
    synchronized (this) {
      effesState = null;
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
      while (runState == RunState.SUSPENDED) {
        effesState = state;
        wait();
      }
      effesState = null;
      if (runState == RunState.STEPPING) {
        runState = RunState.SUSPENDED;
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
