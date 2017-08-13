package com.yuvalshavit.effesvm.runtime;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.function.Function;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.ops.Operation;

public class ProgramCounter {
  private static final State end = new State(null, -1);
  private final State state = new State(end);

  public ProgramCounter(State state) {
    this.state.restoreFrom(state);
  }

  public static State end() {
    return end;
  }

  public int getOpIdx() {
    return state.pc;
  }

  public void setOpIdx(int op) {
    if (op < 0 || op >= state.function.nOps()) {
      throw new IllegalArgumentException("out of range: " + op);
    }
    state.pc = op;
  }

  public void set(EffesFunction<Operation> function, int op) {
    requireNonNull(function, "function");
    state.function = function;
    setOpIdx(op);
  }

  public Operation getOp() {
    return state.function.opAt(getOpIdx());
  }

  public EffesFunction<Operation> getCurrentFunction() {
    return state.function;
  }

  public State save() {
    return new State(state);
  }

  public void restore(State state) {
    this.state.restoreFrom(state);
  }

  @Override
  public String toString() {
    return state.toString();
  }

  public static State firstLineOfFunction(EffesFunction<Operation> function) {
    return new State(function, 0);
  }

  public boolean isAt(State state) {
    return this.state.equals(state);
  }

  public static class State {
    private EffesFunction<Operation> function;
    private int pc;

    private State(EffesFunction<Operation> function, int pc) {
      this.function = function;
      this.pc = pc;
    }

    private State(State copyFom) {
      restoreFrom(copyFom);
    }

    private void restoreFrom(State from) {
      this.function = from.function;
      this.pc = from.pc;
    }

    public EffesFunction<Operation> function() {
      return function;
    }

    public int pc() {
      return pc;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      State state = (State) o;
      return pc == state.pc && Objects.equals(function, state.function);
    }

    @Override
    public int hashCode() {
      return Objects.hash(function, pc);
    }

    @Override
    public String toString() {
      return (this == end) ? "<end>" : String.format("%s[%d]", function, pc);
    }
  }
}
