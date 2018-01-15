package com.yuvalshavit.effesvm.runtime.debugger.msg;

import com.yuvalshavit.effesvm.runtime.debugger.DebuggerState;
import com.yuvalshavit.effesvm.runtime.debugger.msg.Msg;

public class MsgStepIn extends Msg.NoResponse {

  @Override
  void run(DebuggerState state) throws InterruptedException {
    state.step();
  }
}
