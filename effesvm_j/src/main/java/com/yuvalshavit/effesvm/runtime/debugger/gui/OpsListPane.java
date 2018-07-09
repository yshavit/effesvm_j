package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.awt.Color;
import java.awt.Component;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.ops.OpInfo;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgGetModules;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgSetBreakpoints;

class OpsListPane extends AbstractDebugLinePane<OpInfo> {
  private static final MsgGetModules.FunctionInfo unknownFunction = new MsgGetModules.FunctionInfo(
    Collections.singletonList(new OpInfo(new EffesModule.Id("<error>"), "????", Collections.emptyList(), -1, -1, -1)),
    null);

  OpsListPane(Map<EffesFunctionId, MsgGetModules.FunctionInfo> opsByFunction, Supplier<EffesFunctionId> visibleFunction) {
    super(opsByFunction, visibleFunction);
  }

  @Override
  protected MsgSetBreakpoints.Breakpoint getBreakpoint(EffesFunctionId visibleFunction, int clickedItemInList) {
    return new MsgSetBreakpoints.Breakpoint(visibleFunction, clickedItemInList);
  }

  @Override
  public int showFunction(EffesFunctionId functionId, Consumer<OpInfo> addToModel) {
    MsgGetModules.FunctionInfo info = getInfoFor(functionId);
    if (info == null) {
      info = unknownFunction;
    }
    info.ops().forEach(addToModel);
    return -1;
  }

  @Override
  protected int getLineForOp(EffesFunctionId functionId, int opIdxWithinFunction) {
    return opIdxWithinFunction;
  }

  @Override
  protected IntStream getOpsForLine(EffesFunctionId functionId, int lineWithinModel) {
    return IntStream.of(lineWithinModel);
  }

  @Override
  protected void postprocessLine(Component rendered, boolean isDebugEnabled) {
    if (isDebugEnabled) {
      rendered.setForeground(Color.RED);
    }
  }
}
