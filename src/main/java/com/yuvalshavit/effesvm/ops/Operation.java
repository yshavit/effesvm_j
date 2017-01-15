package com.yuvalshavit.effesvm.ops;

import java.util.function.Function;

import com.yuvalshavit.effesvm.runtime.EffesState;
import com.yuvalshavit.effesvm.runtime.PcMove;

public interface Operation extends Function<EffesState,PcMove> {
  abstract class StandardOperation implements Operation {
    abstract void apply0(EffesState state);

    @Override
    public PcMove apply(EffesState state) {
      apply0(state);
      return PcMove.next();
    }
  }
}
