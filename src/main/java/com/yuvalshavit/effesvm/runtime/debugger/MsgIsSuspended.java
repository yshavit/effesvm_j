package com.yuvalshavit.effesvm.runtime.debugger;

public class MsgIsSuspended extends Msg<Boolean> {
  public MsgIsSuspended() {
    super(Boolean.class);
  }

  @Override
  Boolean process(DebuggerState state) {
    return state.isSuspended();
  }
}
