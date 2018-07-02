package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.BitSet;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.ops.OpInfo;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerEvents;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgGetModules;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgSetBreakpoints;

abstract class AbstractDebugLinePane<T> {
  private static final int SCROLLTO_CONTEXT_BUFFER = 5;
  private static final MsgGetModules.FunctionInfo unknownFunction = new MsgGetModules.FunctionInfo(
    Collections.singletonList(new OpInfo(new EffesModule.Id("<error>"), "????", Collections.emptyList(), -1, -1, -1)),
    null);

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
  }

  protected void setCellRenderer(DefaultListCellRenderer cellRenderer) {
    activeOpsList.setCellRenderer(cellRenderer);
  }

  protected MsgGetModules.FunctionInfo getFunctionInfo(EffesFunctionId functionId) {
    return opsByFunction.get(functionId);
  }

  protected abstract MsgSetBreakpoints.Breakpoint getBreakpoint(EffesFunctionId visibleFunction, int clickedItemInList);

  Component getScrollPane() {
    return scrollPane;
  }

  void setActiveFunction(EffesFunctionId functionId, int opIdx) {
    currentFunctionId = functionId;
    currentOpIdx = opIdx;
    showFunction(functionId);
  }

  protected abstract void showFunction(MsgGetModules.FunctionInfo info, Consumer<T> addToModel);
  protected abstract int getLineForOp(int currentOpIdx);

  public void showFunction(EffesFunctionId functionId) {
    activeOpsModel.clear();
    if (functionId == null) {
      return;
    }

    showFunction(opsByFunction.getOrDefault(functionId, unknownFunction), activeOpsModel::addElement);
    if (functionId.equals(currentFunctionId)) {
      int modelIndex = getLineForOp(currentOpIdx);
      activeOpsList.setSelectedIndex(modelIndex);
      Rectangle cellBounds = activeOpsList.getCellBounds(
        Math.max(modelIndex - SCROLLTO_CONTEXT_BUFFER, 0),
        Math.min(modelIndex + SCROLLTO_CONTEXT_BUFFER, activeOpsList.getModel().getSize()));
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
    });
  }




}
