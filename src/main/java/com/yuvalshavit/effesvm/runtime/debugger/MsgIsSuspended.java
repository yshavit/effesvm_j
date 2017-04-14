package com.yuvalshavit.effesvm.runtime.debugger;

import com.yuvalshavit.effesvm.runtime.DebugServerContext;

public class MsgIsSuspended extends Msg<Boolean> {
  public MsgIsSuspended() {
    super(Boolean.class);
  }

  @Override
  Boolean process(DebugServerContext context, DebuggerState state) {
    return state.isSuspended();
  }
}
