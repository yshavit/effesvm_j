package com.yuvalshavit.effesvm.util;

import java.util.function.Consumer;

public class LambdaHelpers {
  private LambdaHelpers() {}

  public static <T> T giveAndTake(T elem, Consumer<? super T> giveTo) {
    giveTo.accept(elem);
    return elem;
  }
}
