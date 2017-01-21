package com.yuvalshavit.effesvm.runtime;

public abstract class BaseEffesType {
  private final String name;

  public BaseEffesType(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }
}
