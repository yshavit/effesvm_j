package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.ops.OpInfo;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgGetModules;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgSetBreakpoints;

class OpsListPane extends AbstractDebugLinePane<OpInfo> {
  private static final MsgGetModules.FunctionInfo unknownFunction = new MsgGetModules.FunctionInfo(
    Collections.singletonList(new OpInfo(new EffesModule.Id("<error>"), "????", Collections.emptyList(), -1, -1, -1)),
    null);

  OpsListPane(Map<EffesFunctionId, MsgGetModules.FunctionInfo> opsByFunction, Supplier<EffesFunctionId> activeFunction) {
    super(opsByFunction, activeFunction);
  }

  @Override
  protected MsgSetBreakpoints.Breakpoint getBreakpoint(EffesFunctionId visibleFunction, int clickedItemInList) {
    return new MsgSetBreakpoints.Breakpoint(visibleFunction, clickedItemInList);
  }

  @Override
  public void showFunction(EffesFunctionId functionId, Consumer<OpInfo> addToModel) {
    MsgGetModules.FunctionInfo info = getInfoFor(functionId);
    if (info == null) {
      info = unknownFunction;
    }
    info.ops().forEach(addToModel);
  }

  @Override
  protected int getLineForOp(EffesFunctionId functionId, int opIdxWithinFunction) {
    return opIdxWithinFunction;
  }

}
