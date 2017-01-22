package com.yuvalshavit.effesvm.load;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Objects;

public class EffesFunction<T> {
  
  public static String MODULE_CLASSNAME = ":";
  
  private final Id id;
  private final int nArgs;
  private final int nVars;
  private final List<T> ops;

  public EffesFunction(Id id, int nVars, int nArgs, List<T> ops) {
    this.id = id;
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
    return ops.size();
  }

  public T opAt(int idx) {
    return ops.get(idx);
  }

  public Id id() {
    return id;
  }

  @Override
  public String toString() {
    return String.valueOf(id);
  }
  
  public static class Id {
    private final String typeName;
    private final String functionName;

    public Id(String typeName, String functionName) {
      this.typeName = requireNonNull(typeName, "typeName");
      this.functionName = requireNonNull(functionName, "functionName");
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
