package com.yuvalshavit.effesvm.runtime.debugger;

import java.util.function.Function;
import java.util.function.Predicate;

import com.yuvalshavit.effesvm.runtime.EffesState;

public class DebuggerState {
  private static final Predicate<EffesState> always = e -> true;
  private static final Predicate<EffesState> never = e -> false;
  private volatile int stepsCompleted;
  private volatile boolean running;
  private Predicate<EffesState> suspendBeforeNextAction;
  private EffesState effesState;

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
      if (suspendBeforeNextAction.test(state)) {
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
