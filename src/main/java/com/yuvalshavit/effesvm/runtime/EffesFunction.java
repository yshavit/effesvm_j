package com.yuvalshavit.effesvm.runtime;

import static com.google.common.base.Preconditions.checkNotNull;

import com.yuvalshavit.effesvm.ops.Operation;

public class EffesFunction {
  
  public static String MODULE_CLASSNAME = ":";
  
  private final String name;
  private final int nArgs;
  private final int nVars;
  private final Operation[] ops;

  public EffesFunction(String name, int nVars, int nArgs, Operation[] ops) {
    this.name = name;
    this.nArgs = nArgs;
    this.nVars = nVars;
    this.ops = ops;
  }

  public int nArgs() {
    return nArgs;
  }

  public int nVars() {
    return nVars;
  }

  public int nOps() {
    return ops.length;
  }

  public Operation opAt(int idx) {
    return ops[idx];
  }

  public String name() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }
  
  public static class Id {
    private final String className;
    private final String functionName;

    public Id(String className, String functionName) {
      this.className = checkNotNull(className, "className");
      this.functionName = checkNotNull(functionName, "functionName");
    }

    public Id(String functionName) {
      this(MODULE_CLASSNAME, functionName);
    }

    @Override
    public String toString() {
      return className + ':' + functionName;
    }
  }
}
