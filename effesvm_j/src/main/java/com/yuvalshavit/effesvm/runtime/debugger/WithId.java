package com.yuvalshavit.effesvm.runtime.debugger;

import java.io.Serializable;

public class WithId<T extends Serializable> implements Serializable {
  private final int id;
  private final T payload;

  public WithId(int id, T payload) {
    this.id = id;
    this.payload = payload;
  }

  public T payload() {
    return payload;
  }

  public int id() {
    return id;
  }

  public <T2 extends Serializable> WithId<T2> withPaylod(T2 payload) {
    return new WithId<>(id, payload);
  }

  @Override
  public String toString() {
    return String.format("#%d. %s", id, payload);
  }
}
