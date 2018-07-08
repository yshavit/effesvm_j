package com.yuvalshavit.effesvm.runtime.debugger.msg;

import com.yuvalshavit.effesvm.runtime.debugger.DebuggerState;

public class MsgStepPast extends MsgResumeBase {
  private final DebuggerState.StepPast which;

  public MsgStepPast(DebuggerState.StepPast which) {
    this.which = which;
  }

  @Override
  void run(DebuggerState state) throws InterruptedException {
    state.stepPastLine(which);
  }

  @Override
  public String toString() {
    return String.format("%s %s", getClass().getSimpleName(), which);
  }
}
