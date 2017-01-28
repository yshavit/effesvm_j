package com.yuvalshavit.effesvm.runtime;

public abstract class EffesRef<T extends BaseEffesType> {
  private final T type;

  protected EffesRef(T type) {
    this.type = type;
  }

  public T type() {
    return type;
  }

  public abstract String toString(boolean useArgNames);

  @Override
  public String toString() {
    return toString(false);
  }

}
