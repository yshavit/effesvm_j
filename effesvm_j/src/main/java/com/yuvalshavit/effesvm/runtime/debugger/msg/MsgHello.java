package com.yuvalshavit.effesvm.runtime.debugger.msg;

import com.yuvalshavit.effesvm.runtime.debugger.DebuggerState;
import com.yuvalshavit.effesvm.runtime.debugger.msg.Msg;

public class MsgHello extends Msg.NoResponse {
  public MsgHello() {
    super();
  }

  @Override
  void run(DebuggerState state) throws InterruptedException {
    // nothing
  }
}
