package com.yuvalshavit.effesvm.runtime;

import static com.yuvalshavit.effesvm.runtime.EffesNativeObject.forString;
import static com.yuvalshavit.effesvm.util.ExtraAsserts.assertExceptionThrown;
import static com.yuvalshavit.effesvm.util.LambdaHelpers.consumeAndReturn;
import static org.testng.Assert.*;

import java.util.Collections;
import java.util.List;

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
    EffesNativeObject.EffesString pushed = consumeAndReturn(forString("elem"), state::push);
    assertEquals(state.pop(), pushed);
  }

  @Test
  public void copyFirstArgToStack() {
    EffesState state = new EffesState(0, forString("first"), forString("second"), forString("third"));
    state.pushVar(0);
    assertEquals(forString("first"), state.pop());
  }

  @Test
  public void copyLastArgToStack() {
    EffesState state = new EffesState(0, forString("first"), forString("second"), forString("third"));
    state.pushVar(2);
    assertEquals(forString("third"), state.pop());
  }

  @Test
  public void copyOutOfRangeArgToStack() {
    EffesState state = new EffesState(0, forString("first"), forString("second"), forString("third"));
    assertExceptionThrown(() -> state.pushVar(4), EffesRuntimeException.class);
    assertExceptionThrown(() -> state.pushVar(-1), EffesRuntimeException.class);
  }

  @Test
  public void copyUnsetVarToStack() {
    EffesState state = new EffesState(1);
    assertExceptionThrown(() -> state.pushVar(0), EffesState.EffesStackException.class);
  }

  @Test
  public void copyToFirstVarAndBack() {
    EffesState state = new EffesState(3);
    EffesNativeObject.EffesString pushed = consumeAndReturn(forString("test item"), state::push);
    state.popToVar(0);
    // check we can't pop a variable, then copy it from the local register and try again
    assertExceptionThrown(state::pop, EffesRuntimeException.class);
    state.pushVar(0);
    assertEquals(state.pop(), pushed);
  }

  @Test
  public void copyToThirdVarAndBack() {
    EffesState state = new EffesState(3);
    EffesNativeObject.EffesString pushed = consumeAndReturn(forString("test item"), state::push);
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
    state.push(forString("test element"));
    assertExceptionThrown(() -> state.openFrame(2, true, 0), EffesRuntimeException.class);
  }

  @Test
  public void frameMultiTestWithRv() {
    // It's most convenient to just do these all at once.
    // We want to test:
    // (1) passing of vars
    // (2) accessing those vars
    // (3) returning a value
    EffesState state = new EffesState(0);
    state.push(forString("junk variable"));
    state.push(forString("first arg"));
    state.push(forString("second arg"));
    state.openFrame(2, true, 0);
    state.pushVar(0);
    assertEquals(state.pop(), forString("first arg"));
    state.pushVar(1);
    state.closeFrame();
    assertEquals(forString("second arg"), state.pop());
  }

  @Test
  public void closeFrameNoRv() {
    // It's most convenient to just do these all at once.
    // We want to test:
    // (1) passing of vars
    // (2) accessing those vars
    // (3) returning a value
    EffesState state = new EffesState(0);
    state.push(forString("only arg"));
    state.openFrame(0, false, 0);
    state.closeFrame();
    assertEquals(forString("only arg"), state.pop());
  }

  @Test
  public void closeFrameHasRvWithNothingOnLocalStack() {
    EffesState state = new EffesState(0);
    state.openFrame(0, true, 0);
    assertExceptionThrown(state::closeFrame, EffesRuntimeException.class);
  }

  @Test
  public void closeFrameHasRvWithTwoItemsOnLocalStack() {
    EffesState state = new EffesState(0);
    state.openFrame(0, true, 0);
    state.push(forString("first"));
    state.push(forString("second"));
    assertExceptionThrown(state::closeFrame, EffesRuntimeException.class);
  }

  @Test
  public void closeFrameNoRvWithOneItemOnLocalStack() {
    EffesState state = new EffesState(0);
    state.openFrame(0, false, 0);
    state.push(forString("only"));
    assertExceptionThrown(state::closeFrame, EffesRuntimeException.class);
  }

  @Test
  public void stackOverflow() {
    EffesState state = new EffesState(ProgramCounter.end(), 5, 0);
    state.push(forString("three spaces left")); // one item is implicitly added for the first frame
    state.push(forString("two spaces left"));
    state.push(forString("one spaces left"));
    state.push(forString("zero spaces left"));
    assertExceptionThrown(() -> state.push(forString("no space left")), EffesState.EffesStackOverflowException.class);
  }
}
