package com.yuvalshavit.effesvm.load;

import java.util.List;

import com.yuvalshavit.effesvm.ops.Operation;

public class EffesFunction {

  private final EffesFunctionId id;
  private final int nArgs;
  private final boolean hasRv;
  private int nVars = -1;
  private List<Operation> ops;

  public EffesFunction(EffesFunctionId id, boolean hasRv, int nArgs) {
    this.id = id;
    this.nArgs = nArgs;
    this.hasRv = hasRv;
  }

  public void setOps(List<Operation> ops) {
    if (this.ops != null) {
      throw new IllegalArgumentException("ops already set");
    }
    this.ops = ops;
  }

  public void setNVars(int nVars) {
    if (this.nVars >= 0) {
      throw new IllegalStateException("nVars already set");
    }
    this.nVars = nVars;
  }

  public int nArgs() {
    return nArgs;
  }

  public boolean hasRv() {
    return hasRv;
  }

  public int nVars() {
    if (nVars < 0) {
      throw new IllegalStateException("nVars not set");
    }
    return nVars;
  }

  public int nOps() {
    return ops().size();
  }

  public Operation opAt(int idx) {
    return ops().get(idx);
  }

  public EffesFunctionId id() {
    return id;
  }

  @Override
  public String toString() {
    return String.valueOf(id);
  }

  private List<Operation> ops() {
    if (ops == null) {
      throw new IllegalArgumentException("ops not set");
    }
    return ops;
  }
}
