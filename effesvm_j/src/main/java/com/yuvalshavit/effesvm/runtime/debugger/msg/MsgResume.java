package com.yuvalshavit.effesvm.runtime.debugger.msg;

import com.yuvalshavit.effesvm.runtime.debugger.DebuggerState;

public class MsgResume extends MsgResumeBase {
  @Override
  void run(DebuggerState state) {
    state.resume();
  }
}
