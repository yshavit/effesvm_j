package com.yuvalshavit.effesvm.runtime;

public class OpContext {
  private final EffesState state;
  private final EffesModule module; // TODO multi-module effes will have to change this

  public OpContext(EffesState state, EffesModule module) {
    this.state = state;
    this.module = module;
  }

  public EffesState state() {
    return state;
  }

  public EffesModule module() {
    return module;
  }
}
