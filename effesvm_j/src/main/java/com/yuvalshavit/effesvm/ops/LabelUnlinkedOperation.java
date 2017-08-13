package com.yuvalshavit.effesvm.ops;

import java.util.function.Supplier;

import com.yuvalshavit.effesvm.load.LinkContext;

public class LabelUnlinkedOperation implements UnlinkedOperation {
  private final String label;
  private final Supplier<Operation> body;

  public LabelUnlinkedOperation(String label, Supplier<Operation> body) {
    this.label = label;
    this.body = body;
  }

  public String label() {
    return label;
  }

  @Override
  public String toString() {
    return "label " + label;
  }

  @Override
  public Operation apply(LinkContext linkContext) {
    return body.get();
  }

  public static class Body {
    private final String label;

    public Body(String label) {
      this.label = label;
    }

    public String get() {
      return label;
    }
  }
}
