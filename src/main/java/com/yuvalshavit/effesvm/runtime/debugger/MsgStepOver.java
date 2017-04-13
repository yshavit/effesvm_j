package com.yuvalshavit.effesvm.runtime.debugger;

public class MsgStepOver extends Msg<MsgHello.Ok> {
  public MsgStepOver() {
    super(MsgHello.Ok.class);
  }

  @Override
  MsgHello.Ok process(DebuggerState state) throws InterruptedException {
    state.stepOver();
    return new Ok();
  }
}
