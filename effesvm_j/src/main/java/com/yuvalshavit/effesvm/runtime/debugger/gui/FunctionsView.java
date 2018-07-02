package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.swing.JLabel;
import javax.swing.JPanel;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerEvents;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerGuiState;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgGetModules;

class FunctionsView {

  private final FunctionPicker functionPicker;
  private final OpsListPane opsListPane;
  private final SourceDebugPane sourceDebugPane;
  private final Container rootContent;

  public FunctionsView(
    DebuggerGuiState saveState,
    Map<EffesModule.Id, Map<EffesFunctionId, MsgGetModules.FunctionInfo>> functionsByModules,
    DebuggerEvents debuggerEvents)
  {
    functionPicker = new FunctionPicker(functionsByModules, debuggerEvents);
    opsListPane = new OpsListPane(
      saveState,
      functionsByModules
        .values()
        .stream()
        .flatMap(m -> m.entrySet().stream())
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
      functionPicker::getActiveFunction);
    sourceDebugPane = new SourceDebugPane();
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
    rootContent.add(opsListPane.getScrollPane(), BorderLayout.CENTER);

    EnumMap<FunctionPicker.SourceType,Supplier<Component>> viewsPerType = new EnumMap<>(FunctionPicker.SourceType.class);
    viewsPerType.put(FunctionPicker.SourceType.EFCT, opsListPane::getScrollPane);
    viewsPerType.put(FunctionPicker.SourceType.SOURCE, sourceDebugPane::getScrollPane);
    functionPicker.addSourceViewListener(sourceType -> {
      Component show = viewsPerType.getOrDefault(sourceType, () -> new JLabel("Unknown view type: " + sourceType)).get();
      Component active = borderLayout.getLayoutComponent(BorderLayout.CENTER);
      if (show != active) {
        rootContent.remove(active);
        rootContent.add(show, BorderLayout.CENTER);
        rootContent.repaint();
      }
    });

    opsListPane.openConnection(debuggerEvents, rootContent::repaint);
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
