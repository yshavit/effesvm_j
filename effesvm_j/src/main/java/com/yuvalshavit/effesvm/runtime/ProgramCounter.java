package com.yuvalshavit.effesvm.runtime;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.Objects;

import com.yuvalshavit.effesvm.load.EfctScope;
import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.ops.OpInfo;
import com.yuvalshavit.effesvm.ops.Operation;

public class ProgramCounter {
  private static final EffesFunction bootstrap = createBootstrap();
  private static final State start = new State(bootstrap, -1);
  private static final State end = new State(bootstrap, 0);

  private static EffesFunction createBootstrap() {
    EffesModule.Id bootstrapModule = new EffesModule.Id("$bootstrap");
    EffesFunction function = new EffesFunction(new EffesFunctionId(EfctScope.ofStatic(bootstrapModule), "$"), true, 1);
    function.setOps(Collections.singletonList(new Operation() {
      private OpInfo info = new OpInfo(bootstrapModule, "<end>", Collections.emptyList(), -1, -1, -1);

      @Override
      public OpInfo info() {
        return info;
      }

      @Override
      public PcMove apply(EffesState effesState) {
        return PcMove.stay();
      }
    }));
    function.setNVars(1);
    return function;
  }

  private final State state = new State(start);

  public ProgramCounter(State state) {
    this.state.restoreFrom(state);
  }

  public static State start() {
    return start;
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

  public void set(EffesFunction function, int op) {
    requireNonNull(function, "function");
    state.function = function;
    setOpIdx(op);
  }

  public Operation getOp() {
    return state.function.opAt(getOpIdx());
  }

  public EffesFunction getCurrentFunction() {
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

  public static State firstLineOfFunction(EffesFunction function) {
    return new State(function, 0);
  }

  public boolean isAt(State state) {
    return this.state.equals(state);
  }

  public static class State {
    private EffesFunction function;
    private int pc;

    private State(EffesFunction function, int pc) {
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

    public EffesFunction function() {
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
      if (this == start) {
        return "start";
      } else if (this == end) {
        return "<end>";
      } else if (function == null) {
        return "<no function>";
      } else {
        return function.id().toString("@" + pc);
      }
    }
  }
}
