package com.yuvalshavit.effesvm.runtime;

import static com.yuvalshavit.effesvm.util.LambdaHelpers.consumeAndReturn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Stream;

public class EffesState {
  private final Object[] stack;
  private int regSp;
  private int regFp;
  private ProgramCounter regPc;
  private String lastSeenLabel;

  public EffesState(ProgramCounter.State pcState, int stackSize, int nLocalVars, EffesRef<?>... args) {
    stack = new Object[stackSize];
    regSp = -1;
    for (EffesRef<?> arg : args) {
      push(arg);
    }
    regPc = new ProgramCounter(pcState);
    doOpenFrame(args.length, nLocalVars, true); // first frame always has an rv; see getFinalPop()
  }

  EffesState(int nLocalVars, EffesRef<?>... args) {
    this(ProgramCounter.end(), 500, nLocalVars, args);
  }

  public void push(EffesRef<?> o) {
    pushObj(o);
  }

  private void pushObj(Object o) {
    if (regSp + 1 >= stack.length) {
      long nFrames = Stream.of(stack).filter(e -> e instanceof FrameInfo).count();
      throw new EffesStackOverflowException(String.format("stackSize=%d, nFrames=%d", stack.length, nFrames));
    }
    stack[++regSp] = o;
  }

  public EffesRef<?> pop() {
    // Can only pop from this frame's local stack!
    if (regSp <= regFp) {
      throw new EffesStackException("underflow");
    }
    EffesRef<?> popped = ((EffesRef<?>) stack[regSp]);
    stack[regSp--] = null;
    return popped;
  }

  EffesRef<?> getFinalPop() {
    // Special variant of pop() for when the item to be popped is the last one there. This should only be called after the very earlier frame,
    // the one that was implicitly created in the constructor, has been closed.
    if (regSp != 0) {
      throw new EffesStackException("items left on the stack");
    }
    return ((EffesRef<?>) stack[0]);
  }

  /**
   * Returns the element at <i>$sp - distanceFromSp</i> on the local stack. For instance, <code>peek(0)</code> returns the top of the local stack,
   * <code>peek(1)</code> returns the element right before it, etc.
   */
  public EffesRef<?> peek(int distanceFromSp) {
    if (distanceFromSp >= getLocalStackSize()) {
      throw new EffesStackException("out of range peek: " + distanceFromSp);
    }
    return (EffesRef<?>) stack[regSp - distanceFromSp];
  }

  public void pushVar(int n) {
    int varIndex = getVarAbsoluteIndex(n);
    Object varValue = stack[varIndex];
    if (varValue == null) {
      throw new EffesStackException("variable " + n + " not set");
    }
    pushObj(varValue);
  }

  public void popToVar(int n) {
    int varIdx = getVarAbsoluteIndex(n);
    stack[varIdx] = pop();
  }

  public void seeLabel(String label) {
    if (label == null) {
      label = "<unknown>"; // not expected to happen
    }
    lastSeenLabel = label;
  }

  public void openFrame(int nArgs, boolean hasRv, int nLocal) {
    // we're going to be using the topmost nArgs of the local stack as this frame's args, so we need to check
    // that there are enough.
    // The frame looks like:
    // [ ...         ] <-- $sp
    // [ localStack1 ]
    // [ localStack0 ]
    // [ FrameInfo   ] <-- $fp
    int localStackSize = getLocalStackSize();
    if (localStackSize < nArgs) {
      throw new EffesStackException(
        String.format("trying to open frame with nArgs=%d, local stack size=%d", nArgs, getLocalStackSize()));
    }
    doOpenFrame(nArgs, nLocal, hasRv);
  }

  public void closeFrame() {
    int localStackSize = getLocalStackSize();
    FrameInfo closingFp = fp();
    EffesRef<?> closingFrameRv;
    if (closingFp.hasRv) {
      if (localStackSize != 1) {
        String msg = localStackSize == 0 ? "no value on local stack to return" : "too many values on local stack";
        throw new EffesStackException(msg);
      }
      closingFrameRv = pop();
      if (closingFrameRv == null) {
        throw new EffesStackException("$rv not set");
      }
    } else {
      if (localStackSize != 0) {
        throw new EffesStackException("too many values on local stack");
      }
      closingFrameRv = null;
    }
    for (int targetSp = this.regFp - closingFp.nVars - 1; regSp > targetSp; regSp--) {
      stack[regSp] = null;
    }
    regFp = closingFp.previousFp;
    regPc.restore(closingFp.previousPc);
    lastSeenLabel = closingFp.lastSeenLabel;
    if (closingFrameRv != null) {
      push(closingFrameRv);
    }
  }

  public ProgramCounter pc() {
    return regPc;
  }

  @Override
  public String toString() {
    return String.format("$fp=%d, $sp=%d, $pc=%s %s", regFp, regSp, regPc, describeLabel(lastSeenLabel));
  }

  List<String> toStringList() {
    List<String> res = new ArrayList<>(regSp + 1);
    int marginSize = Integer.valueOf(regSp).toString().length();
    String marginFormat = "[%" + marginSize + "d] ";
    res.add(toString());

    for (int i = regSp; i >= 0; --i) {
      String margin = String.format(marginFormat, i);
      Object elem = stack[i];
      String elemDesc;
      if (elem instanceof FrameInfo) {
        elemDesc = String.format("%s[======= %s =======]", i == regFp ? "* " : "", elem);
      } else {
        elemDesc = String.valueOf(elem);
      }
      res.add(margin + elemDesc);
    }
    return Collections.unmodifiableList(res);
  }

  @SuppressWarnings("unused") // useful in a debugger
  String toStringFull() {
    return consumeAndReturn(new StringJoiner("\n"), j -> toStringList().forEach(j::add)).toString();
  }

  public int getLocalStackSize() {
    return regSp - regFp;
  }

  private void doOpenFrame(int nArgs, int nLocal, boolean hasRv) {
    regSp += nLocal;
    FrameInfo newFrameInfo = new FrameInfo(nArgs, hasRv, nLocal, regFp, regPc.save(), lastSeenLabel);
    pushObj(newFrameInfo);
    regFp = regSp;
    lastSeenLabel = null;
  }

  private int getVarAbsoluteIndex(int var) {
    FrameInfo frameInfo = fp();
    if (var < 0 || var >= frameInfo.nVars) {
      throw new EffesStackException("arg out of range: " + var);
    }
    // args are like:
    // [ FrameInfo ] <- $fp
    // [ varN      ]
    // [ ...       ]
    // [ var0      ]
    // Keep in mind that n is 0-indexed. So for instance, if argN = 3, then we get dist = 3 - 2 = 1,
    // and when n = 0 then we get dist = 3 - 0 = 3.
    int distanceFromFp = frameInfo.nVars - var;
    return regFp - distanceFromFp;
  }

  private FrameInfo fp() {
    return (FrameInfo) stack[regFp];
  }

  private static String describeLabel(String label) {
    return (label == null)
      ? "(no named labels seen)"
      : ("after " + label);
  }

  public String lastSeenLabel() {
    return lastSeenLabel;
  }

  public static class FrameInfo {
    private final int nVars;
    private final boolean hasRv;
    private final int previousFp;
    private final ProgramCounter.State previousPc;
    private final String lastSeenLabel;

    public FrameInfo(int nArgs, boolean hasRv, int nLocalVars, int previousFp, ProgramCounter.State previousPc, String lastSeenLabel) {
      this.nVars = nArgs + nLocalVars;
      this.hasRv = hasRv;
      this.previousFp = previousFp;
      this.previousPc = previousPc;
      this.lastSeenLabel = lastSeenLabel;
    }

    @Override
    public String toString() {
      return String.format("nVars=%d, rv=%s, prevFp=%d, prevPc=%s %s", nVars, hasRv, previousFp, previousPc, lastSeenLabel);
    }
  }

  public static class EffesStackOverflowException extends EffesRuntimeException {
    // This is the only exception that can be generated as a result of correct bytecode (if the compiled program itself
    // was in error), so it gets its own exception class.

    public EffesStackOverflowException(String message) {
      super(message);
    }
  }

  public static class EffesStackException extends EffesRuntimeException {
    public EffesStackException(String message) {
      super(message);
    }
  }
}
