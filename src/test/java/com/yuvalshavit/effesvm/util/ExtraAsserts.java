package com.yuvalshavit.effesvm.util;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;

public class ExtraAsserts {
  private ExtraAsserts() {}

  public static void assertExceptionThrown(RunnableWithException runnable, Matcher<? super Exception> checker) {
    assertNotNull(runnable, "null runnable provided");
    try {
      runnable.run();
      fail("expected exception");
    } catch (Exception e) {
      try {
        MatcherAssert.assertThat(e, checker);
      } catch (AssertionError failure) {
        failure.initCause(e);
        throw failure;
      }
    }
  }

  public static void assertExceptionThrown(RunnableWithException runnable, Class<? extends Exception> expected) {
    assertExceptionThrown(runnable, CoreMatchers.instanceOf(expected));
  }

  public static void assertExceptionThrown(RunnableWithException runnable) {
    assertExceptionThrown(runnable, CoreMatchers.anything());
  }

  public interface RunnableWithException {
    void run() throws Exception;
  }
}
