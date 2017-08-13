package com.yuvalshavit.effesvm.runtime.debugger;

public class MsgSuspend extends Msg.NoResponse {
  @Override
  void run(DebuggerState state) {
    state.suspend();
  }
}
