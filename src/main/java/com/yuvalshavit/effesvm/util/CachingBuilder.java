package com.yuvalshavit.effesvm.util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class CachingBuilder<K,V> {
  private final Map<K,V> cache = new HashMap<>();
  private final Function<? super K, ? extends V> builder;

  public CachingBuilder(Function<? super K,? extends V> builder) {
    this.builder = builder;
  }

  public V get(K key) {
    return cache.computeIfAbsent(key, builder);
  }
}
