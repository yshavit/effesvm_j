package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerEvents;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgGetModules;

class FunctionPicker {
  private final Map<EffesModule.Id, List<EffesFunctionId>> functionNamesByModule;
  private final Map<EffesModule.Id, EffesFunctionId> activeFunctionPerModule;
  private final JComboBox<EffesFunctionId> functionsChooserBox;
  private final DefaultComboBoxModel<EffesFunctionId> functionChooserModel;
  private final JComboBox<EffesModule.Id> modulesChooserBox;
  private final JCheckBox showOpsCheckbox;

  FunctionPicker(
    SourceModeCoordinator sourceModeCoordinator,
    Map<EffesModule.Id, Map<EffesFunctionId, MsgGetModules.FunctionInfo>> functionsByModules,
    DebuggerEvents events)
  {
    functionNamesByModule = new HashMap<>();
    activeFunctionPerModule = new HashMap<>();

    functionsByModules.forEach((moduleId, functions) -> {
      List<EffesFunctionId> functionNames = new ArrayList<>(functions.size());
      functionNamesByModule.put(moduleId, functionNames);
      functions.forEach((functionId, ops) -> functionNames.add(functionId));
      functionNames.sort(GuiUtils.sortByModuleThenFunction);
      activeFunctionPerModule.put(moduleId, functionNames.get(0));
    });

    functionChooserModel = new DefaultComboBoxModel<>();
    modulesChooserBox = createModuleChooser(functionChooserModel, functionsByModules.keySet().toArray(new EffesModule.Id[0]));
    functionsChooserBox = new JComboBox<>(functionChooserModel);
    showOpsCheckbox = new JCheckBox("opcodes", false);
    showOpsCheckbox.setSelected(sourceModeCoordinator.getMode() == SourceModeCoordinator.Mode.EFCT);
    showOpsCheckbox.addActionListener(action -> {
      SourceModeCoordinator.Mode mode = showOpsCheckbox.isSelected()
        ? SourceModeCoordinator.Mode.EFCT
        : SourceModeCoordinator.Mode.SOURCE;
      sourceModeCoordinator.setMode(mode);
    });
    addListener(this::setActiveFunctionForModule);
    events.on(DebuggerEvents.Type.CLOSED, () -> {
      functionsChooserBox.setEnabled(false);
      functionChooserModel.removeAllElements();
      modulesChooserBox.setEnabled(false);
      modulesChooserBox.setModel(new DefaultComboBoxModel<>()); // empty
    });
  }

  Component getModulesChooserBox() {
    return modulesChooserBox;
  }

  Component getFunctionsChooserBox() {
    return functionsChooserBox;
  }

  Component getOpcodesSwitchBox() {
    return showOpsCheckbox;
  }

  void addListener(Consumer<EffesFunctionId> callback) {
    functionsChooserBox.addActionListener(action -> {
      EffesFunctionId functionId = (EffesFunctionId) functionChooserModel.getSelectedItem();
      callback.accept(functionId);
    });
  }

  void setActiveFunctionForModule(EffesFunctionId functionId) {
    if (functionId != null) {
      activeFunctionPerModule.put(functionId.getScope().getModuleId(), functionId);
    }
  }

  public EffesFunctionId getActiveFunction() {
    return (EffesFunctionId) functionsChooserBox.getSelectedItem();
  }

  void setActiveModule(EffesModule.Id module) {
    modulesChooserBox.setSelectedItem(module);
  }

  private JComboBox<EffesModule.Id> createModuleChooser(DefaultComboBoxModel<EffesFunctionId> functionChooserModel, EffesModule.Id[] ids) {
    JComboBox<EffesModule.Id> modulesChooserBox = new JComboBox<>(ids);
    modulesChooserBox.addActionListener(action -> {
      EffesModule.Id module = (EffesModule.Id) modulesChooserBox.getSelectedItem();
      functionChooserModel.removeAllElements();
      EffesFunctionId activeFunction = activeFunctionPerModule.get(module); // save this before the function box's listener overwrites it
      functionNamesByModule.getOrDefault(module, Collections.emptyList()).forEach(functionChooserModel::addElement);
      functionChooserModel.setSelectedItem(activeFunction);
    });
    return modulesChooserBox;
  }
}
