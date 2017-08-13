package com.yuvalshavit.effesvm.runtime.debugger;

import java.io.Serializable;
import java.util.List;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.runtime.DebugServerContext;

public class MsgGetFrame extends Msg<MsgGetFrame.Response> {

  public MsgGetFrame() {
    super(MsgGetFrame.Response.class);
  }

  @Override
  Response process(DebugServerContext context, DebuggerState state) throws InterruptedException {
    return state.visitStateUnderLock(s -> {
      EffesFunction<Operation> function = s.pc().getCurrentFunction();
      return new Response(s.toStringList(), function.moduleId().toString(), function.id().toString(), s.pc().getOpIdx(), state.getStepsCompleted());
    });
  }

  public static class Response implements Serializable {
    private final List<String> elements;
    private final String currentModuleId;
    private final String currentFunctionName;
    private final int opIndex;
    private final int stepsCompleted;

    public Response(List<String> elements, String moduleId, String functionName, int opIndex, int stepsCompleted) {
      this.elements = elements;
      this.currentModuleId = moduleId;
      this.currentFunctionName = functionName;
      this.opIndex = opIndex;
      this.stepsCompleted = stepsCompleted;
    }

    public int getStepsCompleted() {
      return stepsCompleted;
    }

    public List<String> describeElements() {
      return elements;
    }

    public String getCurrentModuleId() {
      return currentModuleId;
    }

    public String getCurrentFunctionName() {
      return currentFunctionName;
    }

    public int getOpIndex() {
      return opIndex;
    }
  }
}
