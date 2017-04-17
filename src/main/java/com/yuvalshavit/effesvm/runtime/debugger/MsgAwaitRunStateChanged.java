package com.yuvalshavit.effesvm.runtime.debugger;

import com.yuvalshavit.effesvm.runtime.DebugServerContext;

public class MsgAwaitRunStateChanged extends Msg<Boolean> {
  public MsgAwaitRunStateChanged() {
    super(Boolean.class);
  }

  @Override
  Boolean process(DebugServerContext context, DebuggerState state) throws InterruptedException {
    return state.awaitRunStateChanged();
  }
}
