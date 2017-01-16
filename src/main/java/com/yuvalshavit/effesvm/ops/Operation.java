package com.yuvalshavit.effesvm.ops;

import java.util.function.Consumer;
import java.util.function.Function;

import com.yuvalshavit.effesvm.runtime.EffesState;
import com.yuvalshavit.effesvm.runtime.OpContext;
import com.yuvalshavit.effesvm.runtime.PcMove;

public interface Operation extends Function<OpContext,PcMove> {
  static Operation withIncementingPc(Consumer<EffesState> op) {
    return context -> {
      op.accept(context.state());
      return PcMove.next();
    };
  }
}
