package com.yuvalshavit.effesvm.runtime;

import java.util.function.Consumer;

import com.yuvalshavit.effesvm.ops.Operation;

public class EffesFunction {
  private final String name; // just needed for debug purposes
  private final int nArgs;
  private final int nVars;
  private final Operation[] ops;

  public EffesFunction(String name, int nArgs, int nVars, Operation[] ops) {
    this.name = name;
    this.nArgs = nArgs;
    this.nVars = nVars;
    this.ops = ops;
  }

  public int getNArgs() {
    return nArgs;
  }

  public int getNVars() {
    return nVars;
  }

  public int opsCount() {
    return ops.length;
  }

  public Operation op(int idx) {
    return ops[idx];
  }

  @Override
  public String toString() {
    return name;
  }
}
