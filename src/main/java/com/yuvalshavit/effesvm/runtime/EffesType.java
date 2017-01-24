package com.yuvalshavit.effesvm.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.load.ScopeId;

public class EffesType extends BaseEffesType {
  private final EffesModule.Id module;
  private final List<String> arguments;

  public EffesType(String name, List<String> arguments) {
    this(EffesModule.Id.of(), name, arguments);
  }

  public EffesType(EffesModule.Id module, String name, List<String> arguments) {
    super(name);
    this.module = module;
    this.arguments = new ArrayList<>(arguments);
  }

  public EffesType withModuleId(EffesModule.Id newModuleId) {
    if (!module.currentModulePlaceholder()) {
      throw new IllegalStateException("type already has a module id: " + newModuleId);
    }
    return new EffesType(newModuleId, name(), arguments);
  }

  public int nArgs() {
    return arguments.size();
  }

  public int argIndex(String argument) {
    int idx = arguments.indexOf(argument);
    if (idx < 0) {
      throw new NoSuchElementException("no such argument: " + argument);
    }
    return idx;
  }

  public String argAt(int idx) {
    return arguments.get(idx);
  }

  @Override
  public String toString() {
    return ScopeId.toString(module, name());
  }
}
