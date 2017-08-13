package com.yuvalshavit.effesvm.util;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.testng.Assert;

public class ExtraAsserts {
  private ExtraAsserts() {}

  public static void assertExceptionThrown(RunnableWithException runnable, Matcher<? super Exception> checker) {
    Assert.assertNotNull(runnable, "null runnable provided");
    try {
      runnable.run();
      Assert.fail("expected exception");
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
