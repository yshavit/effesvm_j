package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.BitSet;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerEvents;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerGuiState;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgGetModules;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgSetBreakpoints;

class OpsListPane {
  private static final int SCROLLTO_CONTEXT_BUFFER = 5;

  private final DebuggerGuiState saveState;
  private final JList<String> activeOpsList;
  private final Map<EffesFunctionId,MsgGetModules.FunctionInfo> opsByFunction;
  private final DefaultListModel<String> activeOpsModel;
  private final JScrollPane opsScrollPane;
  private final Supplier<EffesFunctionId> activeFunction;

  private EffesFunctionId currentFunctionId; // that is, the one that's actually running on the evm
  private int currentOpIdx;

  OpsListPane(DebuggerGuiState saveState, Map<EffesFunctionId, MsgGetModules.FunctionInfo> opsByFunction, Supplier<EffesFunctionId> activeFunction) {
    this.saveState = saveState;
    this.opsByFunction = opsByFunction;
    this.activeFunction = activeFunction;
    activeOpsModel = new DefaultListModel<>();
    activeOpsList = new JList<>(activeOpsModel);

    opsScrollPane = new JScrollPane(activeOpsList);
    opsScrollPane.setPreferredSize(new Dimension(600, 700));
    activeOpsList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    activeOpsList.setCellRenderer(new OpsListCellRenderer(activeFunction));
  }

  Component getScrollPane() {
    return opsScrollPane;
  }

  void openConnection(DebuggerEvents debuggerEvents, Runnable callback) {
    Set<MsgSetBreakpoints.Breakpoint> breakpoints = saveState.getBreakpoints();
    debuggerEvents.on(DebuggerEvents.Type.CLOSED, activeOpsModel::clear);
    debuggerEvents.communicate(new MsgSetBreakpoints(breakpoints, true), ok -> {
      for (MsgSetBreakpoints.Breakpoint breakpoint : breakpoints) {
        MsgGetModules.FunctionInfo fInfo = opsByFunction.get(breakpoint.getFid());
        if (fInfo != null) {
          fInfo.breakpoints().set(breakpoint.getOpIdx());
        }
      }
      callback.run();
      activeOpsList.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2) {
            EffesFunctionId visibleFunction = activeFunction.get();
            int clickedItem = activeOpsList.locationToIndex(e.getPoint());
            BitSet breakpoints = opsByFunction.get(visibleFunction).breakpoints();
            MsgSetBreakpoints.Breakpoint breakpoint = new MsgSetBreakpoints.Breakpoint(visibleFunction, clickedItem);
            boolean on = !breakpoints.get(clickedItem);
            saveState.setBreakpoint(breakpoint, on);
            MsgSetBreakpoints toggleMsg = new MsgSetBreakpoints(Collections.singleton(breakpoint), on);
            debuggerEvents.communicate(toggleMsg, ok -> {
              breakpoints.flip(clickedItem);
              activeOpsList.repaint();
            });
          }
        }
      });
    });
  }

  void setActiveFunction(EffesFunctionId functionId, int opIdx) {
    currentFunctionId = functionId;
    currentOpIdx = opIdx;
    showFunction(functionId);
  }

  public void showFunction(EffesFunctionId functionId) {
    activeOpsModel.clear();
    if (functionId == null) {
      return;
    }
    opsByFunction
      .getOrDefault(functionId, new MsgGetModules.FunctionInfo(Collections.singletonList("ERROR: no function " + functionId), null))
      .opDescriptions()
      .forEach(activeOpsModel::addElement);
    if (functionId.equals(currentFunctionId)) {
      activeOpsList.setSelectedIndex(currentOpIdx);
      Rectangle cellBounds = activeOpsList.getCellBounds(
        Math.max(currentOpIdx - SCROLLTO_CONTEXT_BUFFER, 0),
        Math.min(currentOpIdx + SCROLLTO_CONTEXT_BUFFER, activeOpsList.getModel().getSize()));
      if (cellBounds != null) {
        activeOpsList.scrollRectToVisible(cellBounds);
      }
    }
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
      if (Objects.equals(functionId, currentFunctionId.get()) && activeOpsList.getSelectedIndex() == index) {
        fromSuper.setBackground(Color.LIGHT_GRAY);
      }
      MsgGetModules.FunctionInfo functionInfo = opsByFunction.get(functionId);
      if (functionInfo != null && functionInfo.breakpoints().get(index)) {
        fromSuper.setForeground(Color.RED);
      }
      return fromSuper;
    }
  }
}
