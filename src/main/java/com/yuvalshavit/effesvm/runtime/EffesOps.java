package com.yuvalshavit.effesvm.runtime;

import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.load.EffesLinkException;
import com.yuvalshavit.effesvm.load.EffesLoadException;
import com.yuvalshavit.effesvm.load.LinkContext;
import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.ops.OperationFactory;
import com.yuvalshavit.effesvm.ops.UnlinkedOperation;

public class EffesOps {

  private final EffesIo io;

  public EffesOps(EffesIo io) {
    this.io = io;
  }

  @OperationFactory("arry")
  public static Operation createArray() {
    return Operation.withIncementingPc(s -> {
      int size = popInt(s);
      EffesRef<?> arr = new EffesNativeObject.EffesArray(size);
      s.push(arr);
    });
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
  public static UnlinkedOperation gotoAbs(String n) {
    int idx = nonNegative(n);
    return linkCtx -> {
      PcMove pcMove = pcMoveTo(linkCtx, idx);
      return s -> pcMove;
    };
  }

  @OperationFactory("goif")
  public static UnlinkedOperation gotoIf(String n) {
    return buildGoif(n, Boolean::booleanValue);
  }

  @OperationFactory("gofi")
  public static UnlinkedOperation gotoIfNot(String n) {
    return buildGoif(n, b -> !b);
  }

  @OperationFactory("rtrn")
  public static Operation rtrn() {
    return s -> {
      s.closeFrame();
      return PcMove.stay();
    };
  }

  @OperationFactory("type")
  public static Operation type(String typeName) {
    return typeBuilder(typeName, EffesState::pop);
  }

  @OperationFactory("typp")
  public static Operation typp(String typeName) {
    return typeBuilder(typeName, s -> s.peek(0));
  }

  @OperationFactory("oarg")
  public static UnlinkedOperation objectArg(String typeName, String fieldName) {
    return linkContext -> {
      EffesType type = linkContext.type(typeName);
      int fieldIndex = type.argIndex(fieldName);
      return Operation.withIncementingPc(s -> {
        EffesObject obj = (EffesObject) s.pop();
        if (!obj.type().equals(type)) {
          throw new EffesRuntimeException(String.format("can't fetch %s.%s on an object of type %s", typeName, fieldName, obj.type()));
        }
        EffesRef<?> arg = obj.getArgAt(fieldIndex);
        s.push(arg);
      });
    };
  }

  @OperationFactory("call_Array:store")
  public static Operation arrayStore() {
    return Operation.withIncementingPc(s -> {
      EffesRef<?> value = s.pop();
      int idx = popInt(s);
      EffesNativeObject.EffesArray arr = (EffesNativeObject.EffesArray) s.pop();
      arr.store(idx, value);
    });
  }

  @OperationFactory("call_Array:get")
  public static Operation arrayGet() {
    return Operation.withIncementingPc(s -> {
      int idx = popInt(s);
      EffesNativeObject.EffesArray arr = (EffesNativeObject.EffesArray) s.pop();
      EffesRef<?> value = arr.get(idx);
      s.push(value);
    });
  }

  @OperationFactory("call_Array:len")
  public static Operation arrayLen() {
    return Operation.withIncementingPc(s -> {
      EffesNativeObject.EffesArray arr = (EffesNativeObject.EffesArray) s.pop();
      s.push(EffesNativeObject.forInt(arr.length()));
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
  public static UnlinkedOperation call(String className, String functionName) {
    EffesFunction.Id id = new EffesFunction.Id(className, functionName);
    if (id.typeName().equals(id.functionName())) {
      String typeName = id.typeName();
      // constructor
      return linkCtxt -> {
        EffesType type = linkCtxt.type(typeName);
        return runCtx -> {
          EffesRef<?>[] args = new EffesRef<?>[type.nArgs()];
          for (int i = args.length - 1; i >= 0; --i) {
            args[i] = runCtx.pop();
          }
          EffesObject obj = new EffesObject(type, args);
          runCtx.push(obj);
          return PcMove.next();
        };
      };
    } else {
      // non-constructor function
      return linkCtx -> {
        boolean isInstanceMethod = !EffesFunction.MODULE_CLASSNAME.equals(id.typeName());
        EffesFunction<?> f = linkCtx.getFunctionInfo(id);
        PcMove pcMove = linkCtx.firstOpOf(id);
        return c -> {
          if (f == null) {
            throw new EffesLoadException("link error: no function " + id);
          }
          int nArgs = f.nArgs(); // does not count the "this" reference
          if (isInstanceMethod) {
            Object instance = c.peek(nArgs);
            if (!(instance instanceof EffesObject)) {
              throw new EffesRuntimeException(String.format("instance function %s invoked on non-EffesObject instance: %s", id, instance));
            }
            EffesType instanceType = ((EffesObject) instance).type();
            if (!id.typeName().equals(instanceType.name())) { // TODO multi-module will need to tweak this a bit
              throw new EffesRuntimeException(String.format("instance function %s invoked on wrong EffesObject instance: %s", id, instance));
            }
            ++nArgs; // to include the "this" reference
          }
          PcMove.next().accept(c.pc()); // set up the return jump point
          c.openFrame(nArgs, f.hasRv(), f.nVars());
          return pcMove;
        };
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
      boolean value = popBoolean(s);
      EffesNativeObject.EffesBoolean push = EffesNativeObject.forBoolean(!value);
      s.push(push);
    });
  }

  @OperationFactory("call_Boolean:and")
  public static Operation and() {
    return booleanOp(Boolean::logicalAnd);
  }

  @OperationFactory("call_Boolean:or")
  public static Operation or() {
    return booleanOp(Boolean::logicalOr);
  }

  @OperationFactory("call_Boolean:xor")
  public static Operation xor() {
    return booleanOp(Boolean::logicalXor);
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

  private static UnlinkedOperation buildGoif(String loc, Predicate<Boolean> condition) {
    int idx = nonNegative(loc);
    return linkCtx -> {
      PcMove to = pcMoveTo(linkCtx, idx);
      return s -> {
        boolean top = ((EffesNativeObject.EffesBoolean) s.pop()).asBoolean();
        return condition.test(top) ? to : PcMove.next();
      };
    };
  }

  private static PcMove pcMoveTo(LinkContext linkCtx, int idx) {
    int nOps = linkCtx.getCurrentLinkingFunctionInfo().nOps();
    if (idx >= nOps) {
      throw new EffesLinkException("jump op index is out of range: " + idx);
    }
    return PcMove.absolute(idx);
  }

  private static Operation booleanOp(BinaryOperator<Boolean> op) {
    return Operation.withIncementingPc(s -> {
      boolean a = popBoolean(s);
      boolean b = popBoolean(s);
      boolean result = op.apply(a, b);
      s.push(EffesNativeObject.forBoolean(result));
    });
  }

  private static int nonNegative(String n) {
    int idx = Integer.parseInt(n);
    if (idx < 0) {
      throw new IllegalArgumentException("negative arg: " + n);
    }
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

  private static boolean popBoolean(EffesState s) {
    EffesNativeObject.EffesBoolean popped = (EffesNativeObject.EffesBoolean) s.pop();
    return popped.asBoolean();
  }

  private static int popInt(EffesState s) {
    EffesNativeObject.EffesInteger popped = (EffesNativeObject.EffesInteger) s.pop();
    return popped.value;
  }

  private static String popString(EffesState s) {
    EffesNativeObject.EffesString effesStr = (EffesNativeObject.EffesString) s.pop();
    return effesStr.value;
  }

  private static Operation typeBuilder(String typeName, Function<EffesState,EffesRef<?>> topItem) {
    return Operation.withIncementingPc(s -> {
      EffesRef<?> item = topItem.apply(s);
      boolean rightType = item.type().name().equals(typeName); // TODO will need to be improved for multi-module
      s.push(EffesNativeObject.forBoolean(rightType));
    });
  }

  private interface IntCmp {
    boolean cmp(int lhs, int rhs);
  }
}
