package com.yuvalshavit.effesvm.ops;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.yuvalshavit.effesvm.runtime.EffesState;
import com.yuvalshavit.effesvm.runtime.PcMove;

public interface Operation extends Function<EffesState,PcMove> {

  String opcode();
  List<String> arguments();

  interface Body extends Function<EffesState,PcMove> { }

  static Operation.Body withIncementingPc(Consumer<EffesState> op) {
    return state -> {
      op.accept(state);
      return PcMove.next();
    };
  }
}
