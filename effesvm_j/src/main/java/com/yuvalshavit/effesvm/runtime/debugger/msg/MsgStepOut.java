package com.yuvalshavit.effesvm.runtime.debugger.msg;

import com.yuvalshavit.effesvm.runtime.debugger.DebuggerState;

public class MsgStepOut extends MsgResumeBase {
  @Override
  void run(DebuggerState state) throws InterruptedException {
    state.stepOut();
  }
}
