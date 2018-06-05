package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.awt.BorderLayout;
import java.awt.Container;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.JPanel;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerEvents;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerGuiState;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgGetModules;

class FunctionsView {

  private final FunctionPicker functionPicker;
  private final OpsListPane opsListPane;
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
    functionPicker.addListener(opsListPane::showFunction);

    rootContent = new JPanel();
    rootContent.setLayout(new BorderLayout());

    JPanel selectorGroup = new JPanel();
    selectorGroup.add(functionPicker.getModulesChooserBox());
    selectorGroup.add(functionPicker.getFunctionsChooserBox());
    rootContent.add(selectorGroup, BorderLayout.NORTH);
    rootContent.add(opsListPane.getScrollPane(), BorderLayout.CENTER);

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
  }
}
