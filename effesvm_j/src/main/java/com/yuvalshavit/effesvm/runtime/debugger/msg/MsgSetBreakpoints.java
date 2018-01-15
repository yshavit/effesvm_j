package com.yuvalshavit.effesvm.runtime.debugger.msg;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import com.yuvalshavit.effesvm.runtime.debugger.DebuggerState;
import com.yuvalshavit.effesvm.runtime.debugger.msg.Msg;

public class MsgSetBreakpoints extends Msg.NoResponse {
  private final Collection<Breakpoint> breakpoints;
  private final boolean on;

  public MsgSetBreakpoints(Collection<Breakpoint> breakpoints, boolean on) {
    this.breakpoints = breakpoints;
    this.on = on;
  }

  public MsgSetBreakpoints(String moduleId, String functionId, int opIdx, boolean on) {
    this(Collections.singleton(new Breakpoint(moduleId, functionId, opIdx)), on);
  }

  @Override
  void run(DebuggerState state) throws InterruptedException {
    for (Breakpoint breakpoint : breakpoints) {
      state.setBreakpoint(breakpoint.moduleId, breakpoint.functionId, breakpoint.opIdx, on);
    }
  }

  public static class Breakpoint implements Serializable {
    private final String moduleId;
    private final String functionId;
    private final int opIdx;

    public Breakpoint(String moduleId, String functionId, int opIdx) {
      this.moduleId = moduleId;
      this.functionId = functionId;
      this.opIdx = opIdx;
    }

    public String getModuleId() {
      return moduleId;
    }

    public String getFunctionId() {
      return functionId;
    }

    public int getOpIdx() {
      return opIdx;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Breakpoint that = (Breakpoint) o;
      return opIdx == that.opIdx && Objects.equals(moduleId, that.moduleId) && Objects.equals(functionId, that.functionId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(moduleId, functionId, opIdx);
    }

    @Override
    public String toString() {
      return String.format("%s %s @ %d", moduleId, functionId, opIdx);
    }
  }
}
