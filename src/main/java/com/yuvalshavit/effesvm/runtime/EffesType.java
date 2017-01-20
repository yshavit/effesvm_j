package com.yuvalshavit.effesvm.runtime;

import java.util.ArrayList;
import java.util.List;

public class EffesType {
  private final String name;
  private final List<String> arguments;

  public EffesType(String name, List<String> arguments) {
    this.name = name;
    this.arguments = new ArrayList<>(arguments);
  }

  public String name() {
    return name;
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

  @Override
  public String toString() {
    return name;
  }
}
