package com.yuvalshavit.effesvm.ops;

import com.yuvalshavit.effesvm.load.LinkContext;

public class LabelUnlinkedOperation implements UnlinkedOperation {
  private final String label;

  public LabelUnlinkedOperation(String label) {
    this.label = label;
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
    return Operation.withIncementingPc(s -> {
    });
  }
}
