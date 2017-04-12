package com.yuvalshavit.effesvm.runtime.debugger;

public class MsgStep extends Msg<VmStateDescription> {

  public MsgStep() {
    super(VmStateDescription.class);
  }

  @Override
  VmStateDescription process(DebuggerState state) throws InterruptedException {
    VmStateDescription rv = state.visitStateUnderLock(VmStateDescription::new);
    state.step();
    return rv;
  }
}
