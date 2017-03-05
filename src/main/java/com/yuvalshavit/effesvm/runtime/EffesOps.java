package com.yuvalshavit.effesvm.runtime;

import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.load.EffesLinkException;
import com.yuvalshavit.effesvm.load.EffesLoadException;
import com.yuvalshavit.effesvm.load.LinkContext;
import com.yuvalshavit.effesvm.ops.LabelUnlinkedOperation;
import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.ops.OperationFactory;
import com.yuvalshavit.effesvm.load.ScopeId;
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
  public static Operation pvar(String n) {
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
    return linkCtx -> {
      PcMove pcMove = pcMoveTo(linkCtx, n);
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
  public static UnlinkedOperation type(String typeName) {
    return typeBuilder(typeName, EffesState::pop);
  }

  @OperationFactory("typp")
  public static UnlinkedOperation typp(String typeName) {
    return typeBuilder(typeName, s -> s.peek(0));
  }

  @OperationFactory("pfld")
  public static UnlinkedOperation pushField(String typeName, String fieldName) {
    return fieldOperation(typeName, fieldName, (type, fieldIndex) -> s -> {
      EffesObject obj = (EffesObject) s.pop();
      if (!obj.type().equals(type)) {
        throw new EffesRuntimeException(String.format("can't fetch %s.%s on an object of type %s", type.argAt(fieldIndex), fieldName, obj.type()));
      }
      EffesRef<?> arg = obj.getArgAt(fieldIndex);
      s.push(arg);
    });
  }

  @OperationFactory("sfld")
  public static UnlinkedOperation storeField(String typeName, String fieldName) {
    return fieldOperation(typeName, fieldName, (type, fieldIndex) -> s -> {
      EffesRef<?> newFieldValue = s.pop();
      EffesObject obj = (EffesObject) s.pop();
      if (!obj.type().equals(type)) {
        throw new EffesRuntimeException(String.format("can't fetch %s.%s on an object of type %s", type.argAt(fieldIndex), fieldName, obj.type()));
      }
      obj.storeArgTo(fieldIndex, newFieldValue);
    });
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
  public static UnlinkedOperation call(String scopeSpecifier, String functionName) {
    ScopeId scope = ScopeId.parse(scopeSpecifier);
    if (scope.hasType() && scope.type().equals(functionName)) {
      // constructor
      return linkCtxt -> {
        EffesType type = linkCtxt.type(scope);
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
        boolean isInstanceMethod = scope.hasType();
        EffesFunction<?> f = linkCtx.getFunctionInfo(scope, functionName);
        PcMove pcMove = linkCtx.firstOpOf(scope, functionName);
        EffesType requiredType = isInstanceMethod ? linkCtx.type(scope) : null;
        return c -> {
          if (f == null) {
            throw new EffesLoadException("link error: no such function");
          }
          int nArgs = f.nArgs(); // does not count the "this" reference
          if (isInstanceMethod) {
            Object instance = c.peek(nArgs);
            if (!(instance instanceof EffesObject)) {
              throw new EffesRuntimeException(String.format("instance function invoked on non-EffesObject instance: %s", instance));
            }
            EffesType instanceType = ((EffesObject) instance).type();
            if (!requiredType.equals(instanceType)) {
              throw new EffesRuntimeException(String.format("instance function invoked on wrong EffesObject instance: %s", instance));
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
    return Operation.withIncementingPc(s -> io.errLine(s.getLocalStackSize() > 0
      ? String.valueOf(s.peek(0).toString(true))
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

  @OperationFactory("sbld")
  public Operation sbld() {
    return Operation.withIncementingPc(s -> {
      s.push(new EffesNativeObject.EffesStringBuilder());
    });
  }

  @OperationFactory("call_StringBuilder:add")
  public Operation stringBuilderAdd() {
    return Operation.withIncementingPc(s -> {
      EffesNativeObject.EffesString toAdd = (EffesNativeObject.EffesString) s.pop();
      EffesNativeObject.EffesStringBuilder sb = (EffesNativeObject.EffesStringBuilder) s.pop();
      String valueToAdd = Objects.requireNonNull(toAdd.value, "null value");
      sb.sb.append(valueToAdd);
    });
  }

  @OperationFactory("labl")
  public UnlinkedOperation label(String name) {
    return new LabelUnlinkedOperation(name);
  }

  @OperationFactory("sbld")
  public Operation stringBuilder() {
    return Operation.withIncementingPc(s -> s.push(new EffesNativeObject.EffesStringBuilder()));
  }

  @OperationFactory("call_StringBuilder:add")
  public Operation stringBuilderAdd() {
    return Operation.withIncementingPc(s -> {
      EffesRef<?> toAdd = s.pop();
      EffesNativeObject.EffesStringBuilder sb = (EffesNativeObject.EffesStringBuilder) s.pop();
      sb.add(toAdd);
    });
  }

  private static UnlinkedOperation buildGoif(String loc, Predicate<Boolean> condition) {
    return linkCtx -> {
      PcMove to = pcMoveTo(linkCtx, loc);
      return s -> {
        boolean top = ((EffesNativeObject.EffesBoolean) s.pop()).asBoolean();
        return condition.test(top) ? to : PcMove.next();
      };
    };
  }

  private static PcMove pcMoveTo(LinkContext linkCtx, String dest) {
    int nOps = linkCtx.getCurrentLinkingFunctionInfo().nOps();
    int idx;
    try {
      idx = Integer.parseInt(dest);
    } catch (NumberFormatException e) {
      idx = linkCtx.findLabelOpIndex(dest);
    }
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

  private static UnlinkedOperation fieldOperation(String typeName, String fieldName, FieldOperator op) {
    ScopeId scope = ScopeId.parse(typeName);
    if (!scope.hasType()) {
      throw new IllegalArgumentException("scope specifier " + scope + " needs a type");
    }
    return linkContext -> {
      EffesType type = linkContext.type(scope);
      int fieldIndex = type.argIndex(fieldName);
      return Operation.withIncementingPc(op.fieldOperation(type, fieldIndex));
    };
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

  private static UnlinkedOperation typeBuilder(String typeName, Function<EffesState,EffesRef<?>> topItem) {
    return linkCtx -> {
      BaseEffesType checkForType;
      if (typeName.indexOf(':') >= 0) {
        checkForType = linkCtx.type(linkCtx.scopeIdBuilder().parse(typeName));
      } else {
        checkForType = EffesNativeObject.parseType(typeName);
      }
      return Operation.withIncementingPc(s -> {
        EffesRef<?> item = topItem.apply(s);
        BaseEffesType type = item.type();
        boolean rightType = type.equals(checkForType);
        s.push(EffesNativeObject.forBoolean(rightType));
      });
    };
  }

  private interface FieldOperator {
    Consumer<EffesState> fieldOperation(EffesType type, int fieldIndex);
  }

  private interface IntCmp {
    boolean cmp(int lhs, int rhs);
  }
}
