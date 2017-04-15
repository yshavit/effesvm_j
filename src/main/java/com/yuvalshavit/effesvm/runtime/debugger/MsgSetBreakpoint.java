package com.yuvalshavit.effesvm.runtime.debugger;

public class MsgSetBreakpoint extends Msg.NoResponse {
  private final String moduleId;
  private final String functionId;
  private final int opIdx;
  private final boolean on;

  public MsgSetBreakpoint(String moduleId, String functionId, int opIdx, boolean on) {
    this.moduleId = moduleId;
    this.functionId = functionId;
    this.opIdx = opIdx;
    this.on = on;
  }

  @Override
  void run(DebuggerState state) throws InterruptedException {
    state.setBreakpoint(moduleId, functionId, opIdx, on);
  }
}
