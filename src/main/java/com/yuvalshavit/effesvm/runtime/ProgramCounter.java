package com.yuvalshavit.effesvm.runtime;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.yuvalshavit.effesvm.util.LambdaHelpers;

public class ProgramCounter {
  private static final State end = LambdaHelpers.consumeAndReturn(new State(), s -> {
    s.function = null;
    s.pc = -1;
  });
  private final State state = new State();

  public ProgramCounter(State state) {
    this.state.restoreFrom(state);
  }

  public static State end() {
    return end;
  }

  public int getOp() {
    return state.pc;
  }

  public void setOp(int op) {
    Preconditions.checkElementIndex(op, state.function.opsCount());
    state.pc = op;
  }

  public void set(EffesFunction function, int pc) {
    state.function = checkNotNull(function, "function");
    setOp(pc);
  }

  public State save() {
    State rv = new State();
    rv.restoreFrom(state);
    return rv;
  }

  public void restore(State state) {
    this.state.restoreFrom(state);
  }

  @Override
  public String toString() {
    return state.toString();
  }

  public static class State {
    private EffesFunction function;
    private int pc;

    private State() {}

    private void restoreFrom(State from) {
      this.function = from.function;
      this.pc = from.pc;
    }

    @Override
    public String toString() {
      return Integer.toString(pc);
    }
  }
}
