package com.yuvalshavit.effesvm.ops;

import java.util.function.Consumer;

import com.yuvalshavit.effesvm.runtime.EffesState;

public interface OpBuilder {
  void build(Operation.Body body);
  void build(UnlinkedOperation.Body body);
  void build(LabelUnlinkedOperation.Body body);
  void build(VarUnlinkedOperation.Body body);

  default void withIncementingPc(Consumer<EffesState> op) {
    build(Operation.withIncementingPc(op));
  }
}
