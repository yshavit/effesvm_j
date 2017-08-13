package com.yuvalshavit.effesvm.runtime.debugger;

public class MsgStepIn extends Msg.NoResponse {

  @Override
  void run(DebuggerState state) throws InterruptedException {
    state.step();
  }
}
