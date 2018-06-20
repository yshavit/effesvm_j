package com.yuvalshavit.effesvm.runtime.coverage;

import lombok.AllArgsConstructor;

@AllArgsConstructor
class FunctionDataSummary {
  public final String hash;
  public final boolean[] seenOps;

}
