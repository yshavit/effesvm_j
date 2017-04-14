package com.yuvalshavit.effesvm.runtime.debugger;

public class MsgStepOver extends Msg.NoResponse {
  @Override
  void run(DebuggerState state) throws InterruptedException {
    state.stepOver();
  }
}
