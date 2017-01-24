package com.yuvalshavit.effesvm.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class LambdaHelpers {
  private LambdaHelpers() {}

  /**
   * Gives the provided element to the consumer, and then returns it.
   */
  public static <T> T consumeAndReturn(T elem, Consumer<? super T> giveTo) {
    giveTo.accept(elem);
    return elem;
  }

  public static <I,O> Iterator<O> map(Iterator<? extends I> in, Function<? super I, ? extends O> f) {
    return new Iterator<O>() {
      @Override
      public boolean hasNext() {
        return in.hasNext();
      }

      @Override
      public O next() {
        I orig = in.next();
        return f.apply(orig);
      }
    };
  }

  public static <K,T> Collector<T,?,Map<K,T>> groupByUniquely(Function<? super T, ? extends K> groupBy, String groupByDesc) {
     return Collectors.groupingBy(groupBy, HashMap::new, Collectors.reducing(null, (a, b) -> {
      if (a == null)
        return b;
      if (b == null)
        return a;
      throw new IllegalArgumentException("duplicate " + groupByDesc + ": " + groupBy.apply(a));
    }));

  }
}
