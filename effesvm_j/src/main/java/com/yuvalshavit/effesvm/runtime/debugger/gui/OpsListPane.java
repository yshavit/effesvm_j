package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.awt.Color;
import java.awt.Component;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.ops.OpInfo;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgGetModules;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgSetBreakpoints;

class OpsListPane extends AbstractDebugLinePane<OpInfo> {

  OpsListPane(Map<EffesFunctionId, MsgGetModules.FunctionInfo> opsByFunction, Supplier<EffesFunctionId> activeFunction) {
    super(opsByFunction, activeFunction);
    setCellRenderer(new OpsListCellRenderer(activeFunction));
  }

  @Override
  protected MsgSetBreakpoints.Breakpoint getBreakpoint(EffesFunctionId visibleFunction, int clickedItemInList) {
    return new MsgSetBreakpoints.Breakpoint(visibleFunction, clickedItemInList);
  }

  @Override
  public void showFunction(MsgGetModules.FunctionInfo info, Consumer<OpInfo> addToModel) {
    info.ops().forEach(addToModel);
  }

  @Override
  protected int getLineForOp(int currentOpIdx) {
    return currentOpIdx;
  }

  private class OpsListCellRenderer extends DefaultListCellRenderer {
    private final Supplier<EffesFunctionId> currentFunctionId;
    Pattern lineNumberFinder;

    public OpsListCellRenderer(Supplier<EffesFunctionId> currentFunctionId) {
      this.currentFunctionId = currentFunctionId;
      lineNumberFinder = Pattern.compile("^#(\\d+) *");
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      if (value instanceof String) {
        String valueStr = (String) value;
        Matcher lineNumberMatcher = lineNumberFinder.matcher(valueStr);
        if (lineNumberMatcher.find()) {
          StringBuilder sb = new StringBuilder(valueStr.length() + 10); // +10 is more than enough
          sb.append(index).append('(').append(lineNumberMatcher.group(1)).append(") ");
          sb.append(valueStr, lineNumberMatcher.end(), valueStr.length());
          value = sb.toString();
        }
      }
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
