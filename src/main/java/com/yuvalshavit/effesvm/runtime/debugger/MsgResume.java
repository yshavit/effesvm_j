package com.yuvalshavit.effesvm.runtime.debugger;

public class MsgResume extends Msg.NoResponse {
  @Override
  void run(DebuggerState state) {
    state.resume();
  }
}
