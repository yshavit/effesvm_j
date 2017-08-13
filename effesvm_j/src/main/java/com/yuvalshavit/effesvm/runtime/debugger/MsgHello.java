package com.yuvalshavit.effesvm.runtime.debugger;

public class MsgHello extends Msg.NoResponse {
  public MsgHello() {
    super();
  }

  @Override
  void run(DebuggerState state) throws InterruptedException {
    // nothing
  }
}
