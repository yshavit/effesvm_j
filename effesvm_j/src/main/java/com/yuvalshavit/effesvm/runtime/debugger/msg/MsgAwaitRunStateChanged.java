package com.yuvalshavit.effesvm.runtime.debugger.msg;

import com.yuvalshavit.effesvm.runtime.DebugServerContext;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerState;
import com.yuvalshavit.effesvm.runtime.debugger.msg.Msg;

public class MsgAwaitRunStateChanged extends Msg<Boolean> {
  public MsgAwaitRunStateChanged() {
    super(Boolean.class);
  }

  @Override
  public Boolean process(DebugServerContext context, DebuggerState state) throws InterruptedException {
    return state.awaitRunStateChanged();
  }
}
