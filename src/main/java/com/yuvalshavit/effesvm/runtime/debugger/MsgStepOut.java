package com.yuvalshavit.effesvm.runtime.debugger;

public class MsgStepOut extends Msg.NoResponse {
  @Override
  void run(DebuggerState state) throws InterruptedException {
    state.stepOut();
  }
}
