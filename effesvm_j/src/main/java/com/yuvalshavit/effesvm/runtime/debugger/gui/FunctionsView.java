package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.swing.JLabel;
import javax.swing.JPanel;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerEvents;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerGuiState;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgGetModules;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgSetBreakpoints;

class FunctionsView {

  private final FunctionPicker functionPicker;
  private final OpsListPane opsListPane;
  private final SourceDebugPane sourceDebugPane;
  private final Container rootContent;

  public FunctionsView(
    SourceModeCoordinator sourceModeCoordinator,
    DebuggerGuiState saveState,
    Map<EffesModule.Id, Map<EffesFunctionId, MsgGetModules.FunctionInfo>> functionsByModules,
    DebuggerEvents debuggerEvents)
  {
    functionPicker = new FunctionPicker(sourceModeCoordinator, functionsByModules, debuggerEvents);
    Map<EffesFunctionId, MsgGetModules.FunctionInfo> functionIdsToInfos = functionsByModules
      .values()
      .stream()
      .flatMap(m -> m.entrySet().stream())
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    opsListPane = new OpsListPane(functionIdsToInfos, functionPicker::getActiveFunction);
    sourceDebugPane = new SourceDebugPane(functionIdsToInfos, functionPicker::getActiveFunction);
    functionPicker.addListener(opsListPane::showFunction);
    functionPicker.addListener(sourceDebugPane::showFunction);

    rootContent = new JPanel();
    BorderLayout borderLayout = new BorderLayout();
    rootContent.setLayout(borderLayout);

    JPanel selectorGroup = new JPanel();
    selectorGroup.add(functionPicker.getModulesChooserBox());
    selectorGroup.add(functionPicker.getFunctionsChooserBox());
    selectorGroup.add(functionPicker.getOpcodesSwitchBox());
    rootContent.add(selectorGroup, BorderLayout.NORTH);
    rootContent.add(new JLabel("Loading..."), BorderLayout.CENTER);

    EnumMap<SourceModeCoordinator.Mode,Supplier<Component>> viewsPerType = new EnumMap<>(SourceModeCoordinator.Mode.class);
    viewsPerType.put(SourceModeCoordinator.Mode.EFCT, opsListPane::getScrollPane);
    viewsPerType.put(SourceModeCoordinator.Mode.SOURCE, sourceDebugPane::getScrollPane);
    sourceModeCoordinator.addSourceViewListener(mode -> {
      Component show = viewsPerType.getOrDefault(mode, () -> new JLabel("Unknown view type: " + mode)).get();
      Component active = borderLayout.getLayoutComponent(BorderLayout.CENTER);
      if (show != active) {
        rootContent.remove(active);
        rootContent.add(show, BorderLayout.CENTER);
        rootContent.repaint();
        rootContent.validate();
      }
    });

    openConnection(debuggerEvents, saveState);
  }

  private void openConnection(DebuggerEvents debuggerEvents, DebuggerGuiState saveState) {
    Set<MsgSetBreakpoints.Breakpoint> breakpoints = saveState.getBreakpoints();
    debuggerEvents.communicate(new MsgSetBreakpoints(breakpoints, true), ok -> {
      opsListPane.onConnect(debuggerEvents, breakpoints, saveState::setBreakpoint);
    });
  }

  Container getRootContent() {
    return rootContent;
  }

  void activate(EffesFunctionId functionId, int opIdx) {
    if (!functionId.equals(functionPicker.getActiveFunction())) {
      functionPicker.setActiveFunctionForModule(functionId);
      functionPicker.setActiveModule(functionId.getScope().getModuleId()); // will also update the function, and page in the ops
    }
    opsListPane.setActiveFunction(functionId, opIdx);
    sourceDebugPane.setActiveFunction(functionId, opIdx);
  }
}
