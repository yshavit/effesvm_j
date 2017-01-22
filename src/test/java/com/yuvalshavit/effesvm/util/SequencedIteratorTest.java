package com.yuvalshavit.effesvm.util;

import static com.yuvalshavit.effesvm.util.ExtraAsserts.assertExceptionThrown;
import static org.testng.Assert.*;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;

import org.testng.annotations.Test;

public class SequencedIteratorTest {

  @Test
  public void basicCounting() {
    SequencedIterator<String> iter = iterateUntilInclusive(1);

    assertEquals(iter.hasNext(), true);
    assertEquals(iter.next(), "0");
    assertEquals(iter.count(), 0);

    assertEquals(iter.hasNext(), true);
    assertEquals(iter.next(), "1");
    assertEquals(iter.count(), 1);

    assertEquals(iter.hasNext(), false);
    assertExceptionThrown(iter::next, NoSuchElementException.class);
    assertEquals(iter.count(), 1); // unchanged!
  }

  @Test
  public void viewReflectsChangesToOriginal() {
    SequencedIterator<String> orig = iterateUntilInclusive(2);

    assertEquals(orig.hasNext(), true);
    assertEquals(orig.next(), "0");
    assertEquals(orig.count(), 0);

    assertEquals(orig.hasNext(), true);
    assertEquals(orig.next(), "1");
    assertEquals(orig.count(), 1);

    SequencedIterator<BigDecimal> view = orig.mapView(BigDecimal::new);
    assertEquals(view.count(), 1);

    assertEquals(orig.hasNext(), true);
    assertEquals(orig.next(), "2");
    assertEquals(orig.count(), 2);
    assertEquals(view.count(), 2);

    assertEquals(orig.hasNext(), false);
    assertEquals(view.hasNext(), false);
  }

  @Test
  public void originalReflectsChangesToView() {
    SequencedIterator<String> orig = iterateUntilInclusive(2);

    assertEquals(orig.hasNext(), true);
    assertEquals(orig.next(), "0");
    assertEquals(orig.count(), 0);

    assertEquals(orig.hasNext(), true);
    assertEquals(orig.next(), "1");
    assertEquals(orig.count(), 1);

    SequencedIterator<BigDecimal> view = orig.mapView(BigDecimal::new);
    assertEquals(view.count(), 1);

    assertEquals(view.hasNext(), true);
    assertEquals(view.next(), new BigDecimal("2"));
    assertEquals(view.count(), 2);
    assertEquals(orig.count(), 2);

    assertEquals(orig.hasNext(), false);
    assertEquals(view.hasNext(), false);
  }

  private static SequencedIterator<String> iterateUntilInclusive(int max) {
    Iterator<String> strings = IntStream.rangeClosed(0, max)
      .mapToObj(String::valueOf)
      .iterator();
    return SequencedIterator.wrap(strings);
  }
}
