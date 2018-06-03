package com.yuvalshavit.effesvm.runtime.debugger.msg;

import java.io.Serializable;
import java.util.List;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.runtime.DebugServerContext;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerState;

public class MsgGetFrame extends Msg<MsgGetFrame.Response> {

  public MsgGetFrame() {
    super(MsgGetFrame.Response.class);
  }

  @Override
  public Response process(DebugServerContext context, DebuggerState state) throws InterruptedException {
    return state.visitStateUnderLock(s -> {
      EffesFunction function = s.pc().getCurrentFunction();
      return new Response(s.toStringList(), function.id(), s.pc().getOpIdx(), state.getStepsCompleted());
    });
  }

  public static class Response implements Serializable {
    private final List<String> elements;
    private final int opIndex;
    private final int stepsCompleted;
    private final EffesFunctionId functionId;

    public Response(List<String> elements, EffesFunctionId fid, int opIndex, int stepsCompleted) {
      this.elements = elements;
      functionId = fid;
      this.opIndex = opIndex;
      this.stepsCompleted = stepsCompleted;
    }

    public int getStepsCompleted() {
      return stepsCompleted;
    }

    public List<String> describeElements() {
      return elements;
    }

    public EffesFunctionId getFunctionId() {
      return functionId;
    }

    public int getOpIndex() {
      return opIndex;
    }
  }
}
