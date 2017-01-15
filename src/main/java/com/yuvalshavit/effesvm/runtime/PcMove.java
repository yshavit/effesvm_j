package com.yuvalshavit.effesvm.runtime;

import java.util.function.Consumer;

public class PcMove implements Consumer<ProgramCounter> {
  private final Consumer<ProgramCounter> delegate;
  private final String description;

  private static final PcMove NEXT = new PcMove(pc -> pc.setOpIdx(pc.getOpIdx() + 1), "increment");

  private PcMove(Consumer<ProgramCounter> delegate, String description) {
    this.delegate = delegate;
    this.description = description;
  }

  public static PcMove next() {
    return NEXT;
  }

  public static PcMove absolute(int pc) {
    return new PcMove(pcObj -> pcObj.setOpIdx(pc), "to " + pc);
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
