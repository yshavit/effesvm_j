package com.yuvalshavit.effesvm.ops;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.yuvalshavit.effesvm.load.LinkContext;
import com.yuvalshavit.effesvm.runtime.EffesState;

public class VarUnlinkedOperation implements UnlinkedOperation {
  private final int idx;
  private final Supplier<Operation> body;

  public static VarUnlinkedOperation.Body popToVar(int idx) {
    return new VarUnlinkedOperation.Body("popToVar", idx, s -> s.popToVar(idx));
  }

  public static VarUnlinkedOperation.Body pushVar(int idx) {
    return new VarUnlinkedOperation.Body("pushVar", idx, s -> s.pushVar(idx));
  }

  VarUnlinkedOperation(int idx, Supplier<Operation> body) {
    this.idx = idx;
    this.body = body;
  }

  public int varIndex() {
    return idx;
  }

  @Override
  public Operation apply(LinkContext linkContext) {
    return body.get();
  }

  public static class Body {
    private final String actionDescription;
    private final int varIndex;
    private final Consumer<EffesState> action;

    private Body(String actionDescription, int varIndex, Consumer<EffesState> action) {
      this.actionDescription = actionDescription;
      this.varIndex = varIndex;
      this.action = action;
    }

    public int varIndex() {
      return varIndex;
    }

    public Consumer<EffesState> handler() {
      return action;
    }

    @Override
    public String toString() {
      return actionDescription + ' ' + varIndex;
    }
  }
}
