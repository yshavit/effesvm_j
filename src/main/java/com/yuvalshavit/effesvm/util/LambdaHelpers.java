package com.yuvalshavit.effesvm.util;

import java.util.function.Consumer;

public class LambdaHelpers {
  private LambdaHelpers() {}

  /**
   * Gives the provided element to the consumer, and then returns it.
   */
  public static <T> T consumeAndReturn(T elem, Consumer<? super T> giveTo) {
    giveTo.accept(elem);
    return elem;
  }
}
