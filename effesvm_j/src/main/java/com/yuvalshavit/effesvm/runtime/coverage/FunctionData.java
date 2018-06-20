package com.yuvalshavit.effesvm.runtime.coverage;

import com.yuvalshavit.effesvm.load.EffesFunction;

class FunctionData extends FunctionDataSummary {
  public final EffesFunction function;

  public FunctionData(String hash, boolean[] seenOps, EffesFunction function) {
    super(hash, seenOps);
    this.function = function;
  }
}
