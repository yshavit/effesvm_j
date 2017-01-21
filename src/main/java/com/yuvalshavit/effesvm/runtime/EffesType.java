package com.yuvalshavit.effesvm.runtime;

import java.util.ArrayList;
import java.util.List;

public class EffesType extends BaseEffesType {
  private final List<String> arguments;

  public EffesType(String name, List<String> arguments) {
    super(name);
    this.arguments = new ArrayList<>(arguments);
  }

  public int nArgs() {
    return arguments.size();
  }

  public int argIndex(String argument) {
    int idx = arguments.indexOf(argument);
    if (idx < 0) {
      throw new EffesRuntimeException("no such argument: " + argument);
    }
    return idx;
  }

  public String argAt(int idx) {
    return arguments.get(idx);
  }
}
