package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.awt.Color;
import java.awt.Component;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

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
    setCellRenderer(new OpsListCellRenderer(activeFunction));
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

  private class OpsListCellRenderer extends DefaultListCellRenderer {
    private final Supplier<EffesFunctionId> currentFunctionId;

    public OpsListCellRenderer(Supplier<EffesFunctionId> currentFunctionId) {
      this.currentFunctionId = currentFunctionId;
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      Component fromSuper = super.getListCellRendererComponent(list, value, index, false, cellHasFocus);
      EffesFunctionId functionId = currentFunctionId.get();
      if (Objects.equals(functionId, currentFunctionId.get()) && isSelected) {
        fromSuper.setBackground(Color.LIGHT_GRAY);
      }
      MsgGetModules.FunctionInfo functionInfo = getFunctionInfo(functionId);
      if (functionInfo != null && functionInfo.breakpoints().get(index)) {
        fromSuper.setForeground(Color.RED);
      }
      return fromSuper;
    }
  }
}
