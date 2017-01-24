package com.yuvalshavit.effesvm.load;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Objects;

public class EffesFunction<T> {

  private final Id id;
  private final int nArgs;
  private final boolean hasRv;
  private final int nVars;
  private final List<T> ops;

  public EffesFunction(Id id, int nVars, boolean hasRv, int nArgs, List<T> ops) {
    this.id = id;
    this.nArgs = nArgs;
    this.hasRv = hasRv;
    this.nVars = nVars;
    this.ops = ops;
  }

  public int nArgs() {
    return nArgs;
  }

  public boolean hasRv() {
    return hasRv;
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
    private static final String MODULE_FUNCTION = ":";
    private final String typeName;
    private final String functionName;

    public Id(String typeName, String functionName) {
      this.typeName = requireNonNull(typeName, "typeName");
      this.functionName = requireNonNull(functionName, "functionName");
    }

    public Id(String functionName) {
      this(MODULE_FUNCTION, functionName);
    }

    public String typeName() {
      if (!hasTypeName()) {
        throw new IllegalArgumentException("function is a module function");
      }
      return typeName;
    }

    public boolean hasTypeName() {
      return !MODULE_FUNCTION.equals(typeName);
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
