package com.yuvalshavit.effesvm.runtime.debugger.msg;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerState;

import lombok.Data;

public class MsgSetBreakpoints extends Msg.NoResponse {
  private final Collection<Breakpoint> breakpoints;
  private final boolean on;

  public MsgSetBreakpoints(Collection<Breakpoint> breakpoints, boolean on) {
    this.breakpoints = breakpoints;
    this.on = on;
  }

  public MsgSetBreakpoints(EffesFunctionId fid, int opIdx, boolean on) {
    this(Collections.singleton(new Breakpoint(fid, opIdx)), on);
  }

  @Override
  void run(DebuggerState state) throws InterruptedException {
    for (Breakpoint breakpoint : breakpoints) {
      state.setBreakpoint(breakpoint.fid, breakpoint.opIdx, on);
    }
  }

  @Data
  public static class Breakpoint implements Serializable {
    private final EffesFunctionId fid;
    private final int opIdx;

    @Override
    public String toString() {
      return String.format("%s %s @ %d", fid, opIdx);
    }
  }
}
