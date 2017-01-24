package com.yuvalshavit.effesvm.util;

import java.util.HashMap;
import java.util.Map;

public class Interner<T> {
  private final Map<T,T> map = new HashMap<>();

  public T intern(T item) {
    T res = map.putIfAbsent(item, item);
    return res == null ? item : res;
  }
}
