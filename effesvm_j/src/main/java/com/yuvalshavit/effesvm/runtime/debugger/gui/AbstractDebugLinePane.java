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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.ops.OpInfo;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerEvents;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgGetModules;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgSetBreakpoints;

abstract class AbstractDebugLinePane<T> {
  private static final int SCROLLTO_CONTEXT_BUFFER = 5;

  private final Map<EffesFunctionId,MsgGetModules.FunctionInfo> opsByFunction;
  private final JScrollPane scrollPane;
  private final Supplier<EffesFunctionId> activeFunction;
  private final JList<T> activeOpsList;
  private final DefaultListModel<T> activeOpsModel;

  private EffesFunctionId currentFunctionId; // that is, the one that's actually running on the evm
  private int currentOpIdx;

  protected AbstractDebugLinePane(Map<EffesFunctionId, MsgGetModules.FunctionInfo> opsByFunction, Supplier<EffesFunctionId> activeFunction) {
    this.opsByFunction = opsByFunction;
    this.activeFunction = activeFunction;
    activeOpsModel = new DefaultListModel<>();
    activeOpsList = new JList<>(activeOpsModel);

    scrollPane = new JScrollPane(activeOpsList);
    scrollPane.setPreferredSize(new Dimension(600, 700));
    activeOpsList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

    activeOpsList.setCellRenderer(new OpsListCellRenderer(activeFunction, () -> currentFunctionId));
  }

  protected abstract MsgSetBreakpoints.Breakpoint getBreakpoint(EffesFunctionId visibleFunction, int clickedItemInList);

  protected void preprocessLine(T line, boolean active) {
    // nothing
  }

  Component getScrollPane() {
    return scrollPane;
  }

  protected OpInfo getActiveOpInfo() {
    List<OpInfo> ops = getInfoFor(currentFunctionId).ops();
    return ops.get(currentOpIdx);
  }

  void setActiveFunction(EffesFunctionId functionId, int opIdx) {
    currentFunctionId = functionId;
    currentOpIdx = opIdx;
    showFunction(functionId);
  }

  protected abstract int showFunction(EffesFunctionId functionId, Consumer<T> addToModel);
  protected abstract int getLineForOp(EffesFunctionId functionId, int opIdxWithinFunction);
  protected abstract IntStream getOpsForLine(EffesFunctionId functionId, int lineWithinModel);

  protected MsgGetModules.FunctionInfo getInfoFor(EffesFunctionId functionId) {
    return opsByFunction.get(functionId);
  }

  public void showFunction(EffesFunctionId functionId) {
    activeOpsModel.clear();
    if (functionId == null) {
      return;
    }

    int scrollToLine = showFunction(functionId, activeOpsModel::addElement);
    if (functionId.equals(currentFunctionId)) {
      int modelIndex = getLineForOp(currentFunctionId, currentOpIdx);
      if (modelIndex >= 0) {
        activeOpsList.setSelectedIndex(modelIndex);
        scrollToLine = modelIndex;
      } else {
        System.err.printf("Couldn't find display index for %s #%d%n", currentFunctionId, currentOpIdx);
      }
    }
    if (scrollToLine >= 0) {
      Rectangle cellBounds = activeOpsList.getCellBounds(
        Math.max(scrollToLine - SCROLLTO_CONTEXT_BUFFER, 0),
        Math.min(scrollToLine + SCROLLTO_CONTEXT_BUFFER, activeOpsList.getModel().getSize()));
      if (cellBounds != null) {
        activeOpsList.scrollRectToVisible(cellBounds);
      }
    }
  }

  void onConnect(DebuggerEvents debuggerEvents, Set<MsgSetBreakpoints.Breakpoint> breakpoints, BiConsumer<MsgSetBreakpoints.Breakpoint,Boolean> save) {
    debuggerEvents.on(DebuggerEvents.Type.CLOSED, activeOpsModel::clear);
    for (MsgSetBreakpoints.Breakpoint breakpoint : breakpoints) {
      MsgGetModules.FunctionInfo fInfo = opsByFunction.get(breakpoint.getFid());
      if (fInfo != null) {
        fInfo.breakpoints().set(breakpoint.getOpIdx());
      }
    }
    activeOpsList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          EffesFunctionId visibleFunction = activeFunction.get();
          int clickedItem = activeOpsList.locationToIndex(e.getPoint());
          MsgSetBreakpoints.Breakpoint breakpoint = getBreakpoint(visibleFunction, clickedItem);
          if (breakpoint != null) {
            BitSet breakpoints = opsByFunction.get(visibleFunction).breakpoints();
            boolean on = !breakpoints.get(clickedItem);
            save.accept(breakpoint, on);
            MsgSetBreakpoints toggleMsg = new MsgSetBreakpoints(Collections.singleton(breakpoint), on);
            debuggerEvents.communicate(toggleMsg, ok -> {
              breakpoints.flip(clickedItem);
              activeOpsList.repaint();
            });
          }
        }
      }
    });
  }

  private class OpsListCellRenderer extends DefaultListCellRenderer {
    private final Supplier<EffesFunctionId> currentFunctionId;
    private final Supplier<EffesFunctionId> currentlyRunningFunction;

    public OpsListCellRenderer(Supplier<EffesFunctionId> currentFunctionId, Supplier<EffesFunctionId> currentlyRunningFunction) {
      this.currentFunctionId = currentFunctionId;
      this.currentlyRunningFunction = currentlyRunningFunction;
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      EffesFunctionId visibleFunction = currentFunctionId.get();
      EffesFunctionId currentlyRunningFunctionId = currentlyRunningFunction.get();
      int currentOpLine = getLineForOp(currentlyRunningFunctionId, currentOpIdx);
      boolean isActive = Objects.equals(visibleFunction, currentlyRunningFunctionId) && index == currentOpLine;
      @SuppressWarnings("unchecked")
      T line = (T) value;
      preprocessLine(line, isActive);
      Component fromSuper = super.getListCellRendererComponent(list, value, index, false, cellHasFocus);
      if (isActive) {
        fromSuper.setBackground(Color.LIGHT_GRAY);
      }
      IntStream ops = getOpsForLine(visibleFunction, index);
      MsgGetModules.FunctionInfo functionInfo = getInfoFor(visibleFunction);
      if (functionInfo != null && ops.mapToObj(opIdx -> functionInfo.breakpoints().get(opIdx)).anyMatch(Boolean::booleanValue)) {
        fromSuper.setForeground(Color.RED);
      }
      return fromSuper;
    }


  }

}
