package com.yuvalshavit.effesvm.runtime;

import com.yuvalshavit.effesvm.runtime.BaseEffesType;

public abstract class EffesRef<T extends BaseEffesType> {
  private final T type;

  protected EffesRef(T type) {
    this.type = type;
  }

  public T type() {
    return type;
  }
}
