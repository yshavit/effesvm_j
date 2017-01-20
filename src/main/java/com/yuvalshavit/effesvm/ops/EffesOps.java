package com.yuvalshavit.effesvm.ops;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.MoreObjects;
import com.yuvalshavit.effesvm.load.EffesLoadException;
import com.yuvalshavit.effesvm.runtime.EffesFunction;
import com.yuvalshavit.effesvm.runtime.EffesIo;
import com.yuvalshavit.effesvm.runtime.EffesObject;
import com.yuvalshavit.effesvm.runtime.EffesRuntimeException;
import com.yuvalshavit.effesvm.runtime.EffesState;
import com.yuvalshavit.effesvm.runtime.EffesType;
import com.yuvalshavit.effesvm.runtime.PcMove;

public class EffesOps {

  private final EffesIo io;

  public EffesOps(EffesIo io) {
    this.io = io;
  }

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
    return buildGoif(n, Boolean::booleanValue);
  }

  @OperationFactory("gofi")
  public static Operation gotoIfNot(String n) {
    return buildGoif(n, b -> !b);
  }

  @OperationFactory("rtrn")
  public static Operation rtrn() {
    return c -> {
      c.state().closeFrame();
      return PcMove.stay();
    };
  }

  @OperationFactory("type")
  public static Operation type(String typeName) {
    return Operation.withIncementingPc(s -> {
      Object item = s.pop();
      boolean rightType = (item instanceof EffesObject) && ((EffesObject) item).type().name().equals(typeName); // TODO will need to beef up for multi-module
      s.push(rightType);
    });
  }

  @OperationFactory("oarg")
  public static Operation objectArg(String name) {
    return Operation.withIncementingPc(s -> {
      EffesObject obj = (EffesObject) s.pop();
      Object arg = obj.getArg(name);
      s.push(arg);
    });
  }

  @OperationFactory("call_native:toString")
  public static Operation nativeToString() {
    return Operation.withIncementingPc(s -> {
      Object top = s.pop();
      if (top instanceof EffesObject) {
        throw new EffesRuntimeException("can't invoke call_native:toString on an EffesObject");
      }
      s.push(top.toString());
    });
  }

  @OperationFactory("call_Integer:parse")
  public static Operation parseInt() {
    return Operation.withIncementingPc(s -> {
      String str = (String) s.pop();
      Object parseResult;
      try {
        parseResult = Integer.parseInt(str);
      } catch (NumberFormatException e) {
        parseResult = Boolean.FALSE;
      }
      s.push(parseResult);
    });
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
    if (id.typeName().equals(id.functionName())) {
      String typeName = id.typeName();
      // constructor
      return c -> {
        EffesType type = c.module().getType(typeName);
        Object[] args = new Object[type.nArgs()];
        for (int i = args.length - 1; i >= 0; --i) {
          args[i] = c.state().pop();
        }
        EffesObject obj = new EffesObject(type, args);
        c.state().push(obj);
        return PcMove.next();
      };
    } else {
      // non-constructor function
      return c -> {
        EffesFunction f = c.module().getFunction(id);
        if (f == null) {
          throw new EffesLoadException("link error: no function " + id);
        }
        int nArgs = f.nArgs(); // does not count the "this" reference
        if (!EffesFunction.MODULE_CLASSNAME.equals(id.typeName())) {
          Object instance = c.state().peek(nArgs);
          if (!(instance instanceof EffesObject)) {
            throw new EffesRuntimeException(String.format("instance function %s invoked on non-EffesObject instance: %s", id, instance));
          }
          EffesType instanceType = ((EffesObject) instance).type();
          if (!id.typeName().equals(instanceType.name())) { // TODO multi-module will need to tweak this a bit
            throw new EffesRuntimeException(String.format("instance function %s invoked on wrong EffesObject instance: %s", id, instance));
          }
          ++nArgs; // to include the "this" reference
        }
        PcMove.next().accept(c.state().pc()); // set up the return jump point
        c.state().openFrame(nArgs, f.nVars());
        return f.getJumpToMe();
      };
    }
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
  public Operation debugPrint() {
    return Operation.withIncementingPc(s -> io.errLine(s.getLocalStackSize() >= 0
      ? String.valueOf(s.peek(0))
      : "<the local stack for this frame is empty>"));
  }

  @OperationFactory("bool")
  public static Operation bool(String sValue) {
    boolean bValue;
    if ("True".equals(sValue)) {
      bValue = true;
    } else if ("False".equals(sValue)) {
      bValue = false;
    } else {
      throw new EffesLoadException("invalid argument: " + sValue);
    }
    return Operation.withIncementingPc(s -> s.push(bValue));
  }

  @OperationFactory("call_Boolean:negate")
  public static Operation negate() {
    return Operation.withIncementingPc(s -> {
      Boolean value = (Boolean) s.pop();
      s.push(!value);
    });
  }

  @OperationFactory("call_Boolean:isTrue")
  public static Operation isTrue() {
    return Operation.withIncementingPc(s -> s.push(Boolean.TRUE.equals(s.pop())));
  }

  @OperationFactory("call_Boolean:isFalse")
  public static Operation isFalse() {
    return Operation.withIncementingPc(s -> s.push(Boolean.FALSE.equals(s.pop())));
  }

  @OperationFactory("str")
  public static Operation strPush(String value) {
    return Operation.withIncementingPc(s -> s.push(value));
  }

  @OperationFactory("call_String:len")
  public static Operation stringLen() {
    return Operation.withIncementingPc(s -> {
      String str = (String) s.pop();
      int len = str.codePointCount(0, str.length());
      s.push(len);
    });
  }

  @OperationFactory("call_String:regex")
  public static Operation stringRegex() {
    return Operation.withIncementingPc(s -> {
      String patternStr = (String) s.pop();
      String lookFor = (String) s.pop();
      Matcher matcher = Pattern.compile(patternStr).matcher(lookFor);
      boolean found = matcher.find();
      s.push(found);
    });
  }

  @OperationFactory("call_String:sout")
  public Operation sout() {
    return Operation.withIncementingPc(s -> io.out((String) s.pop()));
  }

  @OperationFactory("call_String:sin")
  public Operation sin() {
    return Operation.withIncementingPc(s -> s.push(MoreObjects.firstNonNull(io.readLine(), false)));
  }

  private static Operation buildGoif(String loc, Predicate<Boolean> condition) {
    int idx = nonNegative(loc);
    PcMove to = PcMove.absolute(idx);
    return c -> {
      Boolean top = (Boolean) c.state().pop();
      return condition.test(top) ? to : PcMove.next();
    };
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
