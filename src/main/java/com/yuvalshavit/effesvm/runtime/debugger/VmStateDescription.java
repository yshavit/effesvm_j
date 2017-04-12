package com.yuvalshavit.effesvm.runtime.debugger;

import java.io.Serializable;

import com.yuvalshavit.effesvm.runtime.EffesState;

public class VmStateDescription implements Serializable {
  private final String desc;

  private VmStateDescription(String desc) {
    this.desc = desc;
  }

  public VmStateDescription(EffesState effesState) {
    this(effesState.pc().toString());
  }

  @Override
  public String toString() {
    return desc;
  }
}
