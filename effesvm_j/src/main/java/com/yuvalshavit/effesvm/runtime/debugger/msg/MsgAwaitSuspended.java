package com.yuvalshavit.effesvm.runtime.debugger.msg;

import com.yuvalshavit.effesvm.runtime.debugger.DebuggerState;

public class MsgAwaitSuspended extends Msg.NoResponse {
  @Override
  void run(DebuggerState state) throws InterruptedException {
    state.awaitSuspension();
  }
}
