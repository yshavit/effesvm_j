package com.yuvalshavit.effesvm.ops;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;

import com.yuvalshavit.effesvm.load.EffesLoadException;
import com.yuvalshavit.effesvm.runtime.*;

public class EffesOps {

  private final EffesIo io;

  public EffesOps(EffesIo io) {
    this.io = io;
  }

  @OperationFactory("int")
  public static Operation pushInt(String value) {
    int asInt = Integer.parseInt(value);
    EffesNativeObject.EffesInteger obj = EffesNativeObject.forInt(asInt);
    return Operation.withIncementingPc(s -> s.push(obj));
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
      EffesRef<?> item = s.pop();
      boolean rightType = item.type().name().equals(typeName); // TODO will need to be improved for multi-module
      s.push(EffesNativeObject.forBoolean(rightType));
    });
  }

  @OperationFactory("oarg")
  public static Operation objectArg(String name) {
    return Operation.withIncementingPc(s -> {
      EffesObject obj = (EffesObject) s.pop();
      EffesRef<?> arg = obj.getArg(name);
      s.push(arg);
    });
  }

  @OperationFactory("call_native:toString")
  public static Operation nativeToString() {
    return Operation.withIncementingPc(s -> {
      EffesRef<?> top = s.pop();
      s.push(EffesNativeObject.forString(top.toString()));
    });
  }

  @OperationFactory("call_Integer:parse")
  public static Operation parseInt() {
    return Operation.withIncementingPc(s -> {
      EffesNativeObject.EffesString str = (EffesNativeObject.EffesString) s.pop();
      EffesRef<?> parseResult;
      try {
        parseResult = EffesNativeObject.forInt(Integer.parseInt(str.value));
      } catch (NumberFormatException e) {
        parseResult = EffesNativeObject.EffesBoolean.FALSE;
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
        EffesRef<?>[] args = new EffesRef<?>[type.nArgs()];
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
    EffesNativeObject eValue = EffesNativeObject.forBoolean(bValue);
    return Operation.withIncementingPc(s -> s.push(eValue));
  }

  @OperationFactory("call_Boolean:negate")
  public static Operation negate() {
    return Operation.withIncementingPc(s -> {
      EffesNativeObject.EffesBoolean popped = (EffesNativeObject.EffesBoolean) s.pop();
      EffesNativeObject.EffesBoolean push = EffesNativeObject.forBoolean(!popped.asBoolean());
      s.push(push);
    });
  }

  @OperationFactory("call_Boolean:isTrue")
  public static Operation isTrue() {
    return Operation.withIncementingPc(s -> s.push(EffesNativeObject.forBoolean(s.pop() == EffesNativeObject.EffesBoolean.TRUE)));
  }

  @OperationFactory("call_Boolean:isFalse")
  public static Operation isFalse() {
    return Operation.withIncementingPc(s -> s.push(EffesNativeObject.forBoolean(s.pop() == EffesNativeObject.EffesBoolean.FALSE)));
  }

  @OperationFactory("call_Match:igroup")
  public static Operation matchIndexedGroup() {
    return Operation.withIncementingPc(s -> {
      int idx = popInt(s);
      EffesNativeObject.EffesMatch match = (EffesNativeObject.EffesMatch) s.pop();
      s.push(match.group(idx));
    });
  }

  @OperationFactory("call_Match:ngroup")
  public static Operation matchNamedGroup() {
    return Operation.withIncementingPc(s -> {
      String name = popString(s);
      EffesNativeObject.EffesMatch match = (EffesNativeObject.EffesMatch) s.pop();
      s.push(match.group(name));
    });
  }

  @OperationFactory("call_Match:groupCount")
  public static Operation matchGroupCount() {
    return Operation.withIncementingPc(s -> {
      EffesNativeObject.EffesMatch match = (EffesNativeObject.EffesMatch) s.pop();
      s.push(match.groupCount());
    });
  }

  @OperationFactory("str")
  public static Operation strPush(String value) {
    EffesNativeObject eStr = EffesNativeObject.forString(value);
    return Operation.withIncementingPc(s -> s.push(eStr));
  }

  @OperationFactory("call_String:len")
  public static Operation stringLen() {
    return Operation.withIncementingPc(s -> {
      String str = popString(s);
      int len = str.codePointCount(0, str.length());
      s.push(EffesNativeObject.forInt(len));
    });
  }

  @OperationFactory("call_String:regex")
  public static Operation stringRegex() {
    return Operation.withIncementingPc(s -> {
      String patternStr = popString(s);
      String lookFor = popString(s);
      s.push(EffesNativeObject.tryMatch(lookFor, patternStr));
    });
  }

  @OperationFactory("call_String:sout")
  public Operation sout() {
    return Operation.withIncementingPc(s -> io.out(popString(s)));
  }

  @OperationFactory("call_String:sin")
  public Operation sin() {
    return Operation.withIncementingPc(s -> {
      String raw = io.readLine();
      EffesRef<?> eRef = (raw == null) ? EffesNativeObject.EffesBoolean.FALSE : EffesNativeObject.forString(raw);
      s.push(eRef);
    });
  }

  private static Operation buildGoif(String loc, Predicate<Boolean> condition) {
    int idx = nonNegative(loc);
    PcMove to = PcMove.absolute(idx);
    return c -> {
      boolean top = ((EffesNativeObject.EffesBoolean) c.state().pop()).asBoolean();
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
      int rhs = popInt(s);
      int lhs = popInt(s);
      s.push(EffesNativeObject.forInt(op.applyAsInt(lhs, rhs)));
    });
  }

  private static Operation intCmp(IntCmp intCmp) {
    return Operation.withIncementingPc(s -> {
      int rhs = popInt(s);
      int lhs = popInt(s);
      s.push(EffesNativeObject.forBoolean(intCmp.cmp(lhs, rhs)));
    });
  }

  private static int popInt(EffesState s) {
    EffesNativeObject.EffesInteger popped = (EffesNativeObject.EffesInteger) s.pop();
    return popped.value;
  }

  private static String popString(EffesState s) {
    EffesNativeObject.EffesString effesStr = (EffesNativeObject.EffesString) s.pop();
    return effesStr.value;
  }

  private interface IntCmp {
    boolean cmp(int lhs, int rhs);
  }
}
