package com.yuvalshavit.effesvm.runtime.debugger;

public class MsgAwaitSuspension extends Msg.NoResponse {

  @Override
  void run(DebuggerState state) throws InterruptedException {
    state.awaitSuspension();
  }
}
