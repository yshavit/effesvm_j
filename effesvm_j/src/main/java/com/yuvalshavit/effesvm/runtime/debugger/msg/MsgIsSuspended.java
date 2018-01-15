package com.yuvalshavit.effesvm.runtime.debugger.msg;

import com.yuvalshavit.effesvm.runtime.DebugServerContext;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerState;
import com.yuvalshavit.effesvm.runtime.debugger.msg.Msg;

public class MsgIsSuspended extends Msg<Boolean> {
  public MsgIsSuspended() {
    super(Boolean.class);
  }

  @Override
  public Boolean process(DebugServerContext context, DebuggerState state) {
    return state.isSuspended();
  }
}
