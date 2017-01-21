package com.yuvalshavit.effesvm.util;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;

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
}
