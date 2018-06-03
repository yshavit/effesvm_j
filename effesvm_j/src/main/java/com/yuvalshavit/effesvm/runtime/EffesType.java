package com.yuvalshavit.effesvm.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import com.yuvalshavit.effesvm.load.EffesModule;

public class EffesType extends BaseEffesType {
  private final EffesModule.Id module;
  private final List<String> arguments;

  public EffesType(EffesModule.Id module, String name, List<String> arguments) {
    super(name);
    this.module = module;
    this.arguments = new ArrayList<>(arguments);
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

  public EffesModule.Id moduleId() {
    return module;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EffesType effesType = (EffesType) o;
    return
      Objects.equals(name(), effesType.name()) &&
      Objects.equals(module, effesType.module) &&
      Objects.equals(arguments, effesType.arguments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name(), module, arguments);
  }

  @Override
  public String toString() {
    return String.format("%s:%s", module.getName(), name());
  }
}
