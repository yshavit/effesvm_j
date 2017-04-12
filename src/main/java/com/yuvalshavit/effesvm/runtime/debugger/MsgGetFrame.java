package com.yuvalshavit.effesvm.runtime.debugger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.runtime.EffesState;

public class MsgGetFrame extends Msg<MsgGetFrame.Response> {

  private final String expectedCurrentFunctionId;

  public MsgGetFrame(String expectedCurrentFunctionId) {
    super(MsgGetFrame.Response.class);
    this.expectedCurrentFunctionId = expectedCurrentFunctionId;
  }

  @Override
  Response process(DebuggerState state) throws InterruptedException {
    return state.visitStateUnderLock(s -> {
      List<String> opDesc;
      String currentFunctionId = currentFunctionId(s);
      if (expectedCurrentFunctionId == null || !expectedCurrentFunctionId.equals(currentFunctionId)) {
        EffesFunction<Operation> currentFunction = s.pc().getCurrentFunction();
        opDesc = new ArrayList<>(currentFunction.nOps());
        for (int i = 0; i < currentFunction.nOps(); ++i) {
          opDesc.add(currentFunction.opAt(i).info().toString());
        }
      } else {
        opDesc = null;
      }
      return new Response(s.toStringList(), currentFunctionId, opDesc, s.pc().getOpIdx(), state.getStepsCompleted());
    });
  }

  private static String currentFunctionId(EffesState s) {
    return s.pc().getCurrentFunction().id().toString();
  }

  public static class Response implements Serializable {
    private final List<String> elements;
    private final String currentFunctionId;
    private final List<String> functionOps;
    private final int opIndex;
    private final int stepsCompleted;

    public Response(List<String> elements, String currentFunctionId, List<String> functionOps, int opIndex, int stepsCompleted) {
      this.elements = elements;
      this.currentFunctionId = currentFunctionId;
      this.functionOps = functionOps;
      this.opIndex = opIndex;
      this.stepsCompleted = stepsCompleted;
    }

    public int getStepsCompleted() {
      return stepsCompleted;
    }

    public List<String> describeElements() {
      return elements;
    }

    public String getCurrentFunctionId() {
      return currentFunctionId;
    }

    public List<String> getFunctionOps() {
      return functionOps;
    }

    public int getOpIndex() {
      return opIndex;
    }
  }
}
