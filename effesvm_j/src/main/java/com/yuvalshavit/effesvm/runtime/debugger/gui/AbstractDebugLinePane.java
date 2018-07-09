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
  private final Supplier<EffesFunctionId> visibleFunction;
  private final JList<T> visibleOpsList;
  private final DefaultListModel<T> visibleOpsModel;

  private EffesFunctionId currentlyRunningFunctionId; // that is, the one that's actually running on the evm
  private int currentRunningOpIdx;

  protected AbstractDebugLinePane(Map<EffesFunctionId, MsgGetModules.FunctionInfo> opsByFunction, Supplier<EffesFunctionId> visibleFunction) {
    this.opsByFunction = opsByFunction;
    this.visibleFunction = visibleFunction;
    visibleOpsModel = new DefaultListModel<>();
    visibleOpsList = new JList<>(visibleOpsModel);

    scrollPane = new JScrollPane(visibleOpsList);
    scrollPane.setPreferredSize(new Dimension(600, 700));
    visibleOpsList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

    visibleOpsList.setCellRenderer(new OpsListCellRenderer(visibleFunction, () -> currentlyRunningFunctionId));
  }

  protected abstract MsgSetBreakpoints.Breakpoint getBreakpoint(EffesFunctionId visibleFunction, int clickedItemInList);

  protected void preprocessLine(T line, boolean isCurrentlyRunning, boolean isDebugEnabled) {
    // nothing
  }

  protected void postprocessLine(Component rendered, boolean isDebugEnabled) {
    // nothing
  }

  Component getScrollPane() {
    return scrollPane;
  }

  protected OpInfo getCurrentlyRunningOpInfo() {
    List<OpInfo> ops = getInfoFor(currentlyRunningFunctionId).ops();
    return ops.get(currentRunningOpIdx);
  }

  void setCurrentlyRunningFunction(EffesFunctionId functionId, int opIdx) {
    currentlyRunningFunctionId = functionId;
    currentRunningOpIdx = opIdx;
    showFunction(functionId);
  }

  protected abstract int showFunction(EffesFunctionId functionId, Consumer<T> addToModel);
  protected abstract int getLineForOp(EffesFunctionId functionId, int opIdxWithinFunction);
  protected abstract IntStream getOpsForLine(EffesFunctionId functionId, int lineWithinModel);

  protected MsgGetModules.FunctionInfo getInfoFor(EffesFunctionId functionId) {
    return opsByFunction.get(functionId);
  }

  public void showFunction(EffesFunctionId functionId) {
    visibleOpsModel.clear();
    if (functionId == null) {
      return;
    }

    int scrollToLine = showFunction(functionId, visibleOpsModel::addElement);
    if (functionId.equals(currentlyRunningFunctionId)) {
      int modelIndex = getLineForOp(currentlyRunningFunctionId, currentRunningOpIdx);
      if (modelIndex >= 0) {
        visibleOpsList.setSelectedIndex(modelIndex);
        scrollToLine = modelIndex;
      } else {
        System.err.printf("Couldn't find display index for %s #%d%n", currentlyRunningFunctionId, currentRunningOpIdx);
      }
    }
    if (scrollToLine >= 0) {
      Rectangle cellBounds = visibleOpsList.getCellBounds(
        Math.max(scrollToLine - SCROLLTO_CONTEXT_BUFFER, 0),
        Math.min(scrollToLine + SCROLLTO_CONTEXT_BUFFER, visibleOpsList.getModel().getSize()));
      if (cellBounds != null) {
        visibleOpsList.scrollRectToVisible(cellBounds);
      }
    }
  }

  void onConnect(DebuggerEvents debuggerEvents, Set<MsgSetBreakpoints.Breakpoint> breakpoints, BiConsumer<MsgSetBreakpoints.Breakpoint,Boolean> save) {
    debuggerEvents.on(DebuggerEvents.Type.CLOSED, visibleOpsModel::clear);
    for (MsgSetBreakpoints.Breakpoint breakpoint : breakpoints) {
      MsgGetModules.FunctionInfo fInfo = opsByFunction.get(breakpoint.getFid());
      if (fInfo != null) {
        fInfo.breakpoints().set(breakpoint.getOpIdx());
      }
    }
    visibleOpsList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          EffesFunctionId visibleFunction = AbstractDebugLinePane.this.visibleFunction.get();
          int clickedItem = visibleOpsList.locationToIndex(e.getPoint());
          MsgSetBreakpoints.Breakpoint breakpoint = getBreakpoint(visibleFunction, clickedItem);
          if (breakpoint != null) {
            BitSet breakpoints = opsByFunction.get(visibleFunction).breakpoints();
            boolean on = !breakpoints.get(breakpoint.getOpIdx());
            save.accept(breakpoint, on);
            MsgSetBreakpoints toggleMsg = new MsgSetBreakpoints(Collections.singleton(breakpoint), on);
            debuggerEvents.communicate(toggleMsg, ok -> {
              breakpoints.flip(breakpoint.getOpIdx());
              visibleOpsList.repaint();
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
      int currentlyRunningOpLine = getLineForOp(currentlyRunningFunctionId, currentRunningOpIdx);
      boolean isCurrentlyRunning = Objects.equals(visibleFunction, currentlyRunningFunctionId) && index == currentlyRunningOpLine;
      IntStream ops = getOpsForLine(visibleFunction, index);
      MsgGetModules.FunctionInfo functionInfo = getInfoFor(visibleFunction);
      boolean isDebugEnabled = functionInfo != null && ops.mapToObj(opIdx -> functionInfo.breakpoints().get(opIdx)).anyMatch(Boolean::booleanValue);
      @SuppressWarnings("unchecked")
      T line = (T) value;
      preprocessLine(line, isCurrentlyRunning, isDebugEnabled);
      Component fromSuper = super.getListCellRendererComponent(list, value, index, false, cellHasFocus);
      if (isCurrentlyRunning) {
        fromSuper.setBackground(Color.LIGHT_GRAY);
      }
      postprocessLine(fromSuper, isDebugEnabled);
      return fromSuper;
    }


  }

}
