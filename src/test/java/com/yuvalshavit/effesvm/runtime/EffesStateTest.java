package com.yuvalshavit.effesvm.runtime;

import static com.yuvalshavit.effesvm.util.ExtraAsserts.assertExceptionThrown;
import static com.yuvalshavit.effesvm.util.LambdaHelpers.consumeAndReturn;
import static org.testng.Assert.*;

import org.testng.annotations.Test;

public class EffesStateTest {
  @Test
  public void popWhenEmpty() {
    EffesState state = new EffesState(0);
    assertExceptionThrown(state::pop, EffesRuntimeException.class);
  }

  @Test
  public void pushThenPop() {
    EffesState state = new EffesState(0);
    String pushed = consumeAndReturn("elem", state::push);
    assertEquals(state.pop(), pushed);
  }

  @Test
  public void copyFirstArgToStack() {
    EffesState state = new EffesState(0, "first", "second", "third");
    state.pushArg(0);
    assertEquals("first", state.pop());
  }

  @Test
  public void copyLastArgToStack() {
    EffesState state = new EffesState(0, "first", "second", "third");
    state.pushArg(2);
    assertEquals("third", state.pop());
  }

  @Test
  public void copyOutOfRangeArgToStack() {
    EffesState state = new EffesState(0, "first", "second", "third");
    assertExceptionThrown(() -> state.pushArg(4), EffesRuntimeException.class);
    assertExceptionThrown(() -> state.pushArg(-1), EffesRuntimeException.class);
  }

  @Test
  public void copyUnsetVarToStack() {
    EffesState state = new EffesState(1);
    state.pushVar(0);
    assertNull(state.pop());
  }

  @Test
  public void copyToFirstVarAndBack() {
    EffesState state = new EffesState(3);
    String pushed = consumeAndReturn("test item", state::push);
    state.popToVar(0);
    // check we can't pop a variable, then copy it from the local register and try again
    assertExceptionThrown(state::pop, EffesRuntimeException.class);
    state.pushVar(0);
    assertEquals(state.pop(), pushed);
  }

  @Test
  public void copyToThirdVarAndBack() {
    EffesState state = new EffesState(3);
    String pushed = consumeAndReturn("test item", state::push);
    state.popToVar(2);
    // check we can't pop a variable, then copy it from the local register and try again
    assertExceptionThrown(state::pop, EffesRuntimeException.class);
    state.pushVar(2);
    assertEquals(state.pop(), pushed);
  }

  @Test
  public void openFrameWithNotEnoughArgs() {
    // have the first frame contain 2 local vars, so we can verify that these are *not* included in the local stack count
    EffesState state = new EffesState(2);
    state.push("test element");
    assertExceptionThrown(() -> state.openFrame(2, 0), EffesRuntimeException.class);
  }

  @Test
  public void frameMultiTest() {
    // It's most convenient to just do these all at once.
    // We want to test:
    // (1) passing of vars
    // (2) accessing those vars
    // (3) returning a value
    EffesState state = new EffesState(0);
    state.push("junk variable");
    state.push("first arg");
    state.push("second arg");
    state.openFrame(2, 0);
    state.pushArg(0);
    assertEquals(state.pop(), "first arg");
    state.pushArg(1);
    state.closeFrame();
    assertEquals("second arg", state.pop());
  }

  @Test
  public void closeFrameWithNothingOnLocalStack() {
    EffesState state = new EffesState(0);
    state.openFrame(0, 0);
    assertExceptionThrown(state::closeFrame, EffesRuntimeException.class);
  }

  @Test
  public void closeFrameWithTwoItemsOnLocalStack() {
    EffesState state = new EffesState(0);
    state.openFrame(0, 0);
    state.push("first");
    state.push("second");
    assertExceptionThrown(state::closeFrame, EffesRuntimeException.class);
  }

  @Test
  public void stackOverflow() {
    EffesState state = new EffesState(ProgramCounter.end(), 5, 0);
    state.push("three spaces left"); // one item is implicitly added for the first frame
    state.push("two spaces left");
    state.push("one spaces left");
    state.push("zero spaces left");
    assertExceptionThrown(() -> state.push("no space left"), EffesState.EffesStackOverflowException.class);
  }
}
