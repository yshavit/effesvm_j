package com.yuvalshavit.effesvm.util;

import java.util.Iterator;
import java.util.function.Function;

public abstract class SequencedIterator<T> implements Iterator<T> {

  public static <T> SequencedIterator<T> wrap(Iterator<T> iterator) {
    return new Base<>(iterator);
  }

  private SequencedIterator() {}

  public abstract int count();

  /**
   * Returns a <em>view</em> of this iterator, with all of the elements mapped. Both iterators' <tt>count()</tt>s will always be in sync.
   */
  public <R> SequencedIterator<R> mapView(Function<? super T, ? extends R> f) {
    return new View<>(f);
  }

  private class View<R> extends SequencedIterator<R> {
    private final Function<? super T, ? extends R> mappingFunction;

    public View(Function<? super T,? extends R> mappingFunction) {
      this.mappingFunction = mappingFunction;
    }

    @Override
    public int count() {
      return SequencedIterator.this.count();
    }

    @Override
    public boolean hasNext() {
      return SequencedIterator.this.hasNext();
    }

    @Override
    public R next() {
      T original = SequencedIterator.this.next();
      return mappingFunction.apply(original);
    }
  }


  private static class Base<T> extends SequencedIterator<T> {
    private final Iterator<T> underlying;
    private int count;

    public Base(Iterator<T> underlying) {
      this.underlying = underlying;
      this.count = -1; // so that the first call to next() returns 0
    }

    @Override
    public int count() {
      return count;
    }

    @Override
    public boolean hasNext() {
      return underlying.hasNext();
    }

    @Override
    public T next() {
      T res = underlying.next();
      ++count;
      return res;
    }
  }

}
