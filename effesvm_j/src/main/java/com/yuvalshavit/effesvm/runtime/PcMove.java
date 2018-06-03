package com.yuvalshavit.effesvm.runtime;

import java.util.function.Consumer;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.ops.Operation;

public class PcMove implements Consumer<ProgramCounter> {
  private final Consumer<ProgramCounter> delegate;
  private final String description;

  private static final PcMove NEXT = new PcMove(pc -> pc.setOpIdx(pc.getOpIdx() + 1), "increment");
  private static final PcMove STAY = new PcMove(pc -> {}, "no change");

  private PcMove(Consumer<ProgramCounter> delegate, String description) {
    this.delegate = delegate;
    this.description = description;
  }

  public static PcMove absolute(int pc) {
    return new PcMove(pcObj -> pcObj.setOpIdx(pc), "to " + pc);
  }

  public static PcMove firstCallIn(EffesFunction function) {
    if (function == null) {
      throw new IllegalArgumentException();
    }
    return new PcMove(pc -> pc.set(function, 0), "start of " + function);
  }

  public static PcMove next() {
    return NEXT;
  }

  public static PcMove stay() {
    return STAY;
  }

  @Override
  public void accept(ProgramCounter pc) {
    delegate.accept(pc);
  }

  @Override
  public String toString() {
    return description;
  }
}
