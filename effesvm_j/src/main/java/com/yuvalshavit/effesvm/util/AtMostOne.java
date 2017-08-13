package com.yuvalshavit.effesvm.util;

public class AtMostOne<T> {
  private T elem;

  public void accept(T elem) {
    if (elem != null) {
      if (this.elem == null) {
        this.elem = elem;
      } else {
        throw new RuntimeException(String.format("duplicate element %s (already had %s)", elem, this.elem));
      }
    }
  }

  public T get() {
    return elem;
  }
}
