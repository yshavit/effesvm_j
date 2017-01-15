package com.yuvalshavit.effesvm.runtime;

public class ProgramCounter {
  private final State state = new State();

  public int get() {
    return state.pc;
  }

  public void set(int pc) {
    // TODO validation that the new pc is valid
    state.pc = pc;
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

  public static class State {
    private int pc;
    private State() {}

    private State(State copyFrom) {
      restoreFrom(copyFrom); // make sure restoreFrom stays private or final!
    }

    private void restoreFrom(State from) {
      this.pc = from.pc;
    }

    @Override
    public String toString() {
      return Integer.toString(pc);
    }
  }
}
