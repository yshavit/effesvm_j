package com.yuvalshavit.effesvm.ops;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.function.IntBinaryOperator;

import com.google.common.base.MoreObjects;
import com.yuvalshavit.effesvm.load.EffesLoadExeption;
import com.yuvalshavit.effesvm.runtime.EffesFunction;
import com.yuvalshavit.effesvm.runtime.EffesState;
import com.yuvalshavit.effesvm.runtime.PcMove;

public class EffesOps {

  @OperationFactory("int")
  public static Operation pushInt(String value) {
    int asInt = Integer.parseInt(value);
    return Operation.withIncementingPc(s -> s.push(asInt));
  }

  @OperationFactory("pop")
  public static Operation pop() {
    return Operation.withIncementingPc(EffesState::pop);
  }

  @OperationFactory("parg")
  public static Operation parg(String n) {
    int idx = nonNegative(n);
    return Operation.withIncementingPc(s -> s.pushArg(idx));
  }

  @OperationFactory("pvar")
  public static Operation gvar(String n) {
    int idx = nonNegative(n);
    return Operation.withIncementingPc(s -> s.pushVar(idx));
  }

  @OperationFactory("svar")
  public static Operation svar(String n) {
    int idx = nonNegative(n);
    return Operation.withIncementingPc(s -> s.popToVar(idx));
  }

  @OperationFactory("goto")
  public static Operation gotoAbs(String n) {
    int idx = nonNegative(n);
    PcMove to = PcMove.absolute(idx);
    return s -> to;
  }

  @OperationFactory("goif")
  public static Operation gotoIf(String n) {
    int idx = nonNegative(n);
    PcMove to = PcMove.absolute(idx);
    return c -> {
      Boolean top = (Boolean) c.state().pop();
      return top ? to : PcMove.next();
    };
  }

  @OperationFactory("rtrn")
  public static Operation rtrn() {
    return c -> {
      c.state().closeFrame();
      return PcMove.stay();
    };
  }

  @OperationFactory("type")
  public static Operation type() {
    throw new UnsupportedOperationException(); // TODO
  }

  @OperationFactory("call_Integer:lt")
  public static Operation lt() {
    return intCmp((l, r) -> (l < r));
  }

  @OperationFactory("call_Integer:le")
  public static Operation le() {
    return intCmp((l, r) -> (l <= r));
  }

  @OperationFactory("call_Integer:eq")
  public static Operation eq() {
    return intCmp((l, r) -> (l == r));
  }

  @OperationFactory("call_Integer:ge")
  public static Operation ge() {
    return intCmp((l, r) -> (l >= r));
  }

  @OperationFactory("call_Integer:gt")
  public static Operation gt() {
    return intCmp((l, r) -> (l > r));
  }

  @OperationFactory("call")
  public static Operation call(String className, String functionName) {
    EffesFunction.Id id = new EffesFunction.Id(className, functionName);
    return c -> {
      EffesFunction f = c.module().getFunction(id);
      if (f == null) {
        throw new EffesLoadExeption("link error: no function " + id);
      }
      PcMove.next().accept(c.state().pc()); // set up the return jump point
      c.state().openFrame(f.nArgs(), f.nVars());
      return f.getJumpToMe();
    };
  }

  @OperationFactory("call_Integer:add")
  public static Operation iAdd() {
    return intArith((l, r) -> (l + r));
  }

  @OperationFactory("call_Integer:sub")
  public static Operation iSub() {
    return intArith((l, r) -> (l - r));
  }

  @OperationFactory("call_Integer:mult")
  public static Operation iMul() {
    return intArith((l, r) -> (l * r));
  }

  @OperationFactory("call_Integer:div")
  public static Operation iDiv() {
    return intArith((l, r) -> (l / r));
  }

  @OperationFactory("debug-print")
  public static Operation debugPrint() {
    return Operation.withIncementingPc(s -> System.err.println(MoreObjects.firstNonNull(
      s.peek(),
      "<the local stack for this frame is empty>")));
  }

  private static int nonNegative(String n) {
    int idx = Integer.parseInt(n);
    checkArgument(idx >= 0, "negative arg: " + n);
    return idx;
  }

  private static Operation intArith(IntBinaryOperator op) {
    return Operation.withIncementingPc(s -> {
      int rhs = (Integer) s.pop();
      int lhs = (Integer) s.pop();
      s.push(op.applyAsInt(lhs, rhs));
    });
  }

  private static Operation intCmp(IntCmp intCmp) {
    return Operation.withIncementingPc(s -> {
      int rhs = (Integer) s.pop();
      int lhs = (Integer) s.pop();
      s.push(intCmp.cmp(lhs, rhs));
    });
  }

  private interface IntCmp {
    boolean cmp(int lhs, int rhs);
  }
}
