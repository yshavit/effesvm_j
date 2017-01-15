package com.yuvalshavit.effesvm.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Joiner;

public class EffesState {
  private final Object[] stack;
  private int regSp;
  private int regFp;
  private ProgramCounter regPc;

  public EffesState(ProgramCounter.State pcState, int stackSize, int nLocalVars, Object... args) {
    stack = new Object[stackSize];
    regSp = -1;
    for (Object arg : args) {
      push(arg);
    }
    regPc = new ProgramCounter();
    regPc.restore(pcState);
    doOpenFrame(args.length, nLocalVars);
  }

  EffesState(int nLocalVars, Object... args) {
    this(new ProgramCounter().save(), 500, nLocalVars, args);
  }

  public void push(Object o) {
    if (regSp + 1 >= stack.length) {
      throw new EffesStackOverflowException();
    }
    stack[++regSp] = o;
  }

  public Object pop() {
    // Can only pop from this frame's local stack!
    if (regSp <= regFp + (fp().nLocalVars)) {
      throw new EffesStackException("underflow");
    }
    Object popped = stack[regSp];
    stack[regSp--] = null;
    return popped;
  }

  public void pushArg(int n) {
    FrameInfo frameInfo = fp();
    if (n < 0 || n >= frameInfo.nArgs) {
      throw new EffesStackException("arg out of range: n");
    }
    // args are like:
    // [ FrameInfo ] <- $fp
    // [ argN      ]
    // [ ...       ]
    // [ arg0      ]
    // Keep in mind that n is 0-indexed. So for instance, if argN = 3, then we get dist = 3 - 2 = 1,
    // and when n = 0 then we get dist = 3 - 0 = 3.
    int distanceFromFp = frameInfo.nArgs - n;
    push(stack[regFp - distanceFromFp]);
  }

  public void pushVar(int n) {
    push(stack[localVarIdx(n)]);
  }

  public void popToVar(int n) {
    int varIdx = localVarIdx(n);
    stack[varIdx] = pop();
  }

  public void openFrame(int nArgs, int nLocal) {
    // we're going to be using the topmost nArgs of the local stack as this frame's args, so we need to check
    // that there are enough.
    // The frame looks like:
    // [ ...         ] <-- $sp
    // [ localStack1 ]
    // [ localStack0 ]
    // [ localVarN   ]
    // [ ...         ]
    // [ localVar0   ]
    // [ FrameInfo   ] <-- $fp
    int localStackSize = getLocalStackSize();
    if (localStackSize < nArgs) {
      throw new EffesStackException(
        String.format("trying to open frame with nArgs=%d, local stack size=%d", nArgs, fp().nLocalVars));
    }
    doOpenFrame(nArgs, nLocal);
  }

  public void closeFrame() {
    int localStackSize = getLocalStackSize();
    if (localStackSize != 1) {
      String msg = localStackSize == 0 ? "no value on local stack to return" : "too many values on local stack";
      throw new EffesStackException(msg);
    }
    Object closingFrameRv = pop();
    if (closingFrameRv == null) {
      throw new EffesStackException("$rv not set");
    }
    FrameInfo closingFp = fp();
    for (int targetSp = this.regFp - closingFp.nArgs; regSp > targetSp; regSp--) {
      stack[regSp] = null;
    }
    regFp = closingFp.previousFp;
    regPc.restore(closingFp.previousPc);
    push(closingFrameRv);
  }

  public ProgramCounter pc() {
    return regPc;
  }

  @Override
  public String toString() {
    return String.format("$fp=%d, $sp=%d, $pc=%s", regFp, regSp, regPc);
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

  String toStringFull() {
    return Joiner.on("\n").join(toStringList());
  }

  private int getLocalStackSize() {
    int callerFrameSize = regSp - regFp;
    assert callerFrameSize >= 0 : callerFrameSize;
    return callerFrameSize - fp().nLocalVars;
  }

  private void doOpenFrame(int nArgs, int nLocal) {
    FrameInfo newFrameInfo = new FrameInfo(nArgs, nLocal, regFp, regPc.save());
    push(newFrameInfo);
    regFp = regSp;
    regSp += nLocal;
  }

  private int localVarIdx(int n) {
    FrameInfo frameInfo = fp();
    if (n < 0 || n >= frameInfo.nLocalVars) {
      throw new EffesStackException("local var out of range: " + n);
    }
    // local vars are like:
    // [ localVarN ]
    // [ ...       ]
    // [ localVar0 ]
    // [ FrameInfo ] <- $fp
    return regFp + n + 1;
  }

  private FrameInfo fp() {
    return (FrameInfo) stack[regFp];
  }

  public static class FrameInfo {
    private final int nArgs;
    private final int nLocalVars;
    private final int previousFp;
    private final ProgramCounter.State previousPc;

    public FrameInfo(int nArgs, int nLocalVars, int previousFp, ProgramCounter.State previousPc) {
      this.nArgs = nArgs;
      this.nLocalVars = nLocalVars;
      this.previousFp = previousFp;
      this.previousPc = previousPc;
    }

    @Override
    public String toString() {
      return String.format("nArgs=%d, nLocal=%d, prevFp=%d, prevPc=%s", nArgs, nLocalVars, previousFp, previousPc);
    }
  }

  public static class EffesStackOverflowException extends EffesRuntimeException {
    // This is the only exception that can be generated as a result of correct bytecode (if the compiled program itself
    // was in error), so it gets its own exception class.
  }

  public static class EffesStackException extends EffesRuntimeException {
    public EffesStackException(String message) {
      super(message);
    }
  }
}
