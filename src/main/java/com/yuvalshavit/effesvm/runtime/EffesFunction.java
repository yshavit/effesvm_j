package com.yuvalshavit.effesvm.runtime;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

import com.sun.tools.javac.util.List;
import com.yuvalshavit.effesvm.ops.Operation;

public class EffesFunction {
  
  public static String MODULE_CLASSNAME = ":";
  
  private final String name;
  private final int nArgs;
  private final int nVars;
  private final Operation[] ops;
  private PcMove jumpToMe;

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

  public PcMove getJumpToMe() {
    PcMove res = this.jumpToMe;
    if (jumpToMe == null) {
      // TODO this is a data race, but benign since PcMove happens to use all final fields. But yuck, not great architecture here.
      // Need to rethink dependencies a bit!
      res = PcMove.firstCallIn(this);
      jumpToMe = res;
    }
    return res;
  }

  @Override
  public String toString() {
    return name;
  }
  
  public static class Id {
    private final String typeName;
    private final String functionName;

    public Id(String typeName, String functionName) {
      this.typeName = checkNotNull(typeName, "typeName");
      this.functionName = checkNotNull(functionName, "functionName");
    }

    public Id(String functionName) {
      this(MODULE_CLASSNAME, functionName);
    }

    public String typeName() {
      return typeName;
    }

    public String functionName() {
      return functionName;
    }

    @Override
    public String toString() {
      return typeName + ':' + functionName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Id id = (Id) o;
      return Objects.equals(typeName, id.typeName) && Objects.equals(functionName, id.functionName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(typeName, functionName);
    }

  }
}
