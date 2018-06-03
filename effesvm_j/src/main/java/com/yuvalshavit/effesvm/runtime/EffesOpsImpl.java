package com.yuvalshavit.effesvm.runtime;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.yuvalshavit.effesvm.load.EfctScopeDesc;
import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesLinkException;
import com.yuvalshavit.effesvm.load.EffesLoadException;
import com.yuvalshavit.effesvm.load.LinkContext;
import com.yuvalshavit.effesvm.ops.LabelUnlinkedOperation;
import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.ops.UnlinkedOperation;
import com.yuvalshavit.effesvm.ops.VarUnlinkedOperation;

public class EffesOpsImpl implements EffesOps<Object> {

  private final EffesIo io;

  public EffesOpsImpl(EffesIo io) {
    this.io = io;
  }

  @Override
  public Operation.Body createArray() {
    return Operation.withIncementingPc(s -> {
      int size = popInt(s);
      EffesRef<?> arr = new EffesNativeObject.EffesArray(size);
      s.push(arr);
    });
  }

  @Override
  public Operation.Body pushInt(String value) {
    int asInt = Integer.parseInt(value);
    EffesNativeObject.EffesInteger obj = EffesNativeObject.forInt(asInt);
    return Operation.withIncementingPc(s -> s.push(obj));
  }

  @Override
  public Operation.Body pop() {
    return Operation.withIncementingPc(EffesState::pop);
  }

  @Override
  public Operation.Body copy() {
    return Operation.withIncementingPc(s -> s.push(s.peek(0)));
  }

  @Override
  public VarUnlinkedOperation.Body pvar(String n) {
    int idx = nonNegative(n);
    return VarUnlinkedOperation.pushVar(idx);
  }

  @Override
  public VarUnlinkedOperation.Body svar(String n) {
    int idx = nonNegative(n);
    return VarUnlinkedOperation.popToVar(idx);
  }

  @Override
  public VarUnlinkedOperation.Body Svar(String n) {
    int idx = nonNegative(n);
    return VarUnlinkedOperation.copyToVar(idx);
  }


  @Override
  public UnlinkedOperation.Body gotoAbs(String n) {
    return linkCtx -> {
      PcMove pcMove = pcMoveTo(linkCtx, n);
      return (Operation.Body) s -> pcMove;
    };
  }

  @Override
  public UnlinkedOperation.Body gotoIf(String n) {
    return buildGoif(n, Boolean::booleanValue);
  }

  @Override
  public UnlinkedOperation.Body gotoIfNot(String n) {
    return buildGoif(n, b -> !b);
  }

  @Override
  public Operation.Body rtrn() {
    return s -> {
      s.closeFrame();
      return PcMove.stay();
    };
  }

  @Override
  public UnlinkedOperation.Body type(String typeName) {
    return typeBuilder(typeName, EffesState::pop, (s, i) -> {});
  }

  @Override
  public UnlinkedOperation.Body typp(String typeName) {
    return typeBuilder(typeName, s -> s.peek(0), (s, i) -> {});
  }

  @Override
  public UnlinkedOperation.Body typf(String typeName) {
    return typeBuilder(typeName, EffesState::pop, EffesState::push);
  }

  @Override
  public UnlinkedOperation.Body pushField(String typeName, String fieldName) {
    return pushFieldOperation(typeName, fieldName, EffesState::pop);
  }

  @Override
  public UnlinkedOperation.Body PushField(String typeName, String fieldName) {
    return pushFieldOperation(typeName, fieldName, s -> s.peek(0));
  }

  private UnlinkedOperation.Body pushFieldOperation(String typeName, String fieldName, Function<EffesState,EffesRef<?>> getTop) {
    return fieldOperation(typeName, fieldName, (type, fieldIndex) -> s -> {
      EffesObject obj = (EffesObject) getTop.apply(s);
      if (!obj.type().equals(type)) {
        throw new EffesRuntimeException(String.format("can't fetch %s.%s on an object of type %s", type.argAt(fieldIndex), fieldName, obj.type()));
      }
      EffesRef<?> arg = obj.getArgAt(fieldIndex);
      s.push(arg);
    });
  }

  @Override
  public UnlinkedOperation.Body storeField(String typeName, String fieldName) {
    return fieldOperation(typeName, fieldName, (type, fieldIndex) -> s -> {
      EffesRef<?> newFieldValue = s.pop();
      EffesObject obj = (EffesObject) s.pop();
      if (!obj.type().equals(type)) {
        throw new EffesRuntimeException(String.format("can't fetch %s.%s on an object of type %s", type.argAt(fieldIndex), fieldName, obj.type()));
      }
      obj.storeArgTo(fieldIndex, newFieldValue);
    });
  }

  @Override
  public Operation.Body arrayStore() {
    return Operation.withIncementingPc(s -> {
      EffesRef<?> value = s.pop();
      int idx = popInt(s);
      EffesNativeObject.EffesArray arr = (EffesNativeObject.EffesArray) s.pop();
      arr.store(idx, value);
    });
  }

  @Override
  public Operation.Body arrayGet() {
    return Operation.withIncementingPc(s -> {
      int idx = popInt(s);
      EffesNativeObject.EffesArray arr = (EffesNativeObject.EffesArray) s.pop();
      EffesRef<?> value = arr.get(idx);
      s.push(value);
    });
  }

  @Override
  public Operation.Body arrayLen() {
    return Operation.withIncementingPc(s -> {
      EffesNativeObject.EffesArray arr = (EffesNativeObject.EffesArray) s.pop();
      s.push(EffesNativeObject.forInt(arr.length()));
    });
  }

  @Override
  public Operation.Body nativeToString() {
    return Operation.withIncementingPc(s -> {
      EffesRef<?> top = s.pop();
      s.push(EffesNativeObject.forString(top.toString()));
    });
  }

  @Override
  public Operation.Body nativeToStringPretty() {
    return Operation.withIncementingPc(s -> {
      EffesRef<?> top = s.pop();
      String pretty = top.visit(new EffesRefFormats.Pretty()).toString();
      s.push(EffesNativeObject.forString(pretty));
    });
  }

  @Override
  public Operation.Body parseInt() {
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

  @Override
  public Operation.Body lt() {
    return intCmp((l, r) -> (l < r));
  }

  @Override
  public Operation.Body le() {
    return intCmp((l, r) -> (l <= r));
  }

  @Override
  public Operation.Body eq() {
    return intCmp((l, r) -> (l == r));
  }

  @Override
  public Operation.Body ne() {
    return intCmp((l, r) -> (l != r));
  }

  @Override
  public Operation.Body ge() {
    return intCmp((l, r) -> (l >= r));
  }

  @Override
  public Operation.Body gt() {
    return intCmp((l, r) -> (l > r));
  }

  @Override
  public UnlinkedOperation.Body call(String scopeSpecifier, String functionName) {
    return linkCtx -> {
      EffesFunctionId functionId = EffesFunctionId.parse(scopeSpecifier, functionName, linkCtx.currentModule());
      if (functionId.isConstructor()) {
        // constructor
        EffesType type = functionId.getScope().mapRequiringInstanceType(linkCtx::type);
        return runCtx -> {
          EffesRef<?>[] args = new EffesRef<?>[type.nArgs()];
          for (int i = args.length - 1; i >= 0; --i) {
            args[i] = runCtx.pop();
          }
          EffesObject obj = new EffesObject(type, args);
          runCtx.push(obj);
          return PcMove.next();
        };
      } else {
        // non-constructor function
        EffesFunction f = linkCtx.getFunctionInfo(functionId);
        PcMove pcMove = PcMove.firstCallIn(f);
        EffesType instanceTargetType = functionId.getScope().map(m -> null, linkCtx::type);
        return c -> {
          int nArgs = f.nArgs(); // does not count the "this" reference
          if (instanceTargetType != null) {
            Object instance = c.peek(nArgs);
            if (!(instance instanceof EffesObject)) {
              throw new EffesRuntimeException(String.format("instance function invoked on non-EffesObject instance: %s", instance));
            }
            EffesType instanceType = ((EffesObject) instance).type();
            if (!instanceTargetType.equals(instanceType)) {
              throw new EffesRuntimeException(String.format("instance function invoked on wrong EffesObject instance: %s", instance));
            }
            ++nArgs; // to include the "this" reference
          }
          PcMove.next().accept(c.pc()); // set up the return jump point
          c.openFrame(nArgs, f.hasRv(), f.nVars());
          return pcMove;
        };
      }
    };
  }

  @Override
  public Operation.Body iAdd() {
    return intArith((l, r) -> (l + r));
  }

  @Override
  public Operation.Body iSub() {
    return intArith((l, r) -> (l - r));
  }

  @Override
  public Operation.Body iMul() {
    return intArith((l, r) -> (l * r));
  }

  @Override
  public Operation.Body iDiv() {
    return intArith((l, r) -> (l / r));
  }

  @Override
  public Operation.Body debugPrint() {
    return debugPrint(() -> new EffesRefFormats.Inline(true));
  }

  @Override
  public Operation.Body debugPrintPretty() {
    return debugPrint(EffesRefFormats.Pretty::new);
  }

  private Operation.Body debugPrint(Supplier<EffesRefVisitor> visitor) {
    return Operation.withIncementingPc(s -> {
      String line = s.getLocalStackSize() > 0
        ? String.valueOf(s.peek(0).visit(visitor.get()).toString())
        : "<the local stack for this frame is empty>";
      EffesOutput err = io.err();
      err.write(line);
      err.write("\n");
    });
  }

  @Override
  public Operation.Body bool(String sValue) {
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

  @Override
  public Operation.Body negate() {
    return Operation.withIncementingPc(s -> {
      boolean value = popBoolean(s);
      EffesNativeObject.EffesBoolean push = EffesNativeObject.forBoolean(!value);
      s.push(push);
    });
  }

  @Override
  public Operation.Body and() {
    return booleanOp(Boolean::logicalAnd);
  }

  @Override
  public Operation.Body or() {
    return booleanOp(Boolean::logicalOr);
  }

  @Override
  public Operation.Body xor() {
    return booleanOp(Boolean::logicalXor);
  }

  @Override
  public Operation.Body matchIndexedGroup() {
    return Operation.withIncementingPc(s -> {
      int idx = popInt(s);
      EffesNativeObject.EffesMatch match = (EffesNativeObject.EffesMatch) s.pop();
      s.push(match.group(idx));
    });
  }

  @Override
  public Operation.Body matchNamedGroup() {
    return Operation.withIncementingPc(s -> {
      String name = popString(s);
      EffesNativeObject.EffesMatch match = (EffesNativeObject.EffesMatch) s.pop();
      s.push(match.group(name));
    });
  }

  @Override
  public Operation.Body matchGroupCount() {
    return Operation.withIncementingPc(s -> {
      EffesNativeObject.EffesMatch match = (EffesNativeObject.EffesMatch) s.pop();
      s.push(match.groupCount());
    });
  }

  @Override
  public Operation.Body strPush(String value) {
    EffesNativeObject eStr = EffesNativeObject.forString(value);
    return Operation.withIncementingPc(s -> s.push(eStr));
  }

  @Override
  public Operation.Body stringLen() {
    return Operation.withIncementingPc(s -> {
      String str = popString(s);
      int len = str.codePointCount(0, str.length());
      s.push(EffesNativeObject.forInt(len));
    });
  }

  @Override
  public Operation.Body stringRegex() {
    return Operation.withIncementingPc(s -> {
      String patternStr = popString(s);
      String lookIn = popString(s);
      s.push(EffesNativeObject.tryMatch(lookIn, patternStr));
    });
  }

  @Override
  public Operation.Body sout() {
    return Operation.withIncementingPc(s -> io.out().write(popString(s)));
  }

  @Override
  public Operation.Body concat() {
    return Operation.withIncementingPc(s -> {
      EffesNativeObject.EffesString second = (EffesNativeObject.EffesString) s.pop();
      EffesNativeObject.EffesString first = (EffesNativeObject.EffesString) s.pop();
      EffesNativeObject.EffesString result = EffesNativeObject.forString(first.value + second.value);
      s.push(result);
    });
  }

  @Override
  public Operation.Body stdin() {
    return Operation.withIncementingPc(s -> s.push(new EffesNativeObject.EffesStreamIn(io.in())));
  }

  @Override
  public Operation.Body stdout() {
    return Operation.withIncementingPc(s -> s.push(new EffesNativeObject.EffesStreamOut(io.out())));
  }

  @Override
  public Operation.Body writeFile() {
    return Operation.withIncementingPc(s -> {
      String fileName = popString(s);
      PrintStream printer = new PrintStream(io.writeFile(fileName));

      EffesNativeObject.EffesStreamOut stream = new EffesNativeObject.EffesStreamOut(new EffesOutput.FromWriter(printer, fileName));
      s.push(stream);
    });
  }

  @Override
  public Operation.Body writeText() {
    return Operation.withIncementingPc(s -> {
      EffesNativeObject.EffesString text = (EffesNativeObject.EffesString) s.pop();
      EffesNativeObject.EffesStreamOut streamOut = (EffesNativeObject.EffesStreamOut) s.pop();
      streamOut.get().write(text.value);
    });
  }

  @Override
  public Operation.Body readFile() {
    return Operation.withIncementingPc(s -> {
      String fileName = popString(s);
      BufferedReader reader = new BufferedReader(new InputStreamReader(io.readFile(fileName), StandardCharsets.UTF_8));
      EffesNativeObject.EffesStreamIn stream = new EffesNativeObject.EffesStreamIn(new EffesInput.FromReader(reader, fileName));
      s.push(stream);
    });
  }

  @Override
  public Operation.Body readLine() {
    return Operation.withIncementingPc(s -> {
      EffesNativeObject.EffesStreamIn streamIn = (EffesNativeObject.EffesStreamIn) s.pop();
      pushStringOrFalse(s, streamIn.get().readLine());
    });
  }

  @Override
  public Operation.Body stdinLine() {
    return Operation.withIncementingPc(s -> pushStringOrFalse(s, io.in().readLine()));
  }

  public void pushStringOrFalse(EffesState state, String stringOrNull) {
    state.push((stringOrNull == null) ? EffesNativeObject.EffesBoolean.FALSE : EffesNativeObject.forString(stringOrNull));
  }

  @Override
  public Operation.Body stringBuilderAdd() {
    return Operation.withIncementingPc(s -> {
      EffesNativeObject.EffesString toAdd = (EffesNativeObject.EffesString) s.pop();
      EffesNativeObject.EffesStringBuilder sb = (EffesNativeObject.EffesStringBuilder) s.pop();
      String valueToAdd = Objects.requireNonNull(toAdd.value, "null value");
      sb.sb.append(valueToAdd);
    });
  }

  @Override
  public LabelUnlinkedOperation.Body label(String name) {
    return new LabelUnlinkedOperation.Body(name);
  }

  @Override
  public Operation.Body sbld() {
    return Operation.withIncementingPc(s -> s.push(new EffesNativeObject.EffesStringBuilder()));
  }

  @Override
  public Operation.Body fail(String message) {
    return s -> {
      s.getStackTrace().forEach(System.err::println);
      System.err.flush();
      throw new EffesRuntimeException(message);
    };
  }

  private UnlinkedOperation.Body buildGoif(String loc, Predicate<Boolean> condition) {
    return linkCtx -> {
      PcMove to = pcMoveTo(linkCtx, loc);
      return s -> {
        boolean top = ((EffesNativeObject.EffesBoolean) s.pop()).asBoolean();
        return condition.test(top) ? to : PcMove.next();
      };
    };
  }

  private static PcMove pcMoveTo(LinkContext linkCtx, String dest) {
    int nOps = linkCtx.nOpsInCurrentFunction();
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

  private Operation.Body booleanOp(BinaryOperator<Boolean> op) {
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

  private UnlinkedOperation.Body fieldOperation(String typeName, String fieldName, FieldOperator op) {
    return linkContext -> EfctScopeDesc.parse(typeName, linkContext.currentModule()).mapRequiringInstanceType((m, t) -> {
        EffesType type = linkContext.type(m, t);
        int fieldIndex = type.argIndex(fieldName);
        return Operation.withIncementingPc(op.fieldOperation(type, fieldIndex));
      });
  }

  private Operation.Body intArith(IntBinaryOperator op) {
    return Operation.withIncementingPc(s -> {
      int rhs = popInt(s);
      int lhs = popInt(s);
      s.push(EffesNativeObject.forInt(op.applyAsInt(lhs, rhs)));
    });
  }

  private Operation.Body intCmp(IntCmp intCmp) {
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

  private UnlinkedOperation.Body typeBuilder(String typeName, Function<EffesState,EffesRef<?>> topItem, BiConsumer<EffesState,EffesRef<?>> ifMatched) {
    return linkCtx -> {
      BaseEffesType checkForType;
      if (typeName.indexOf(':') >= 0) {
        checkForType = EfctScopeDesc.parse(typeName, linkCtx.currentModule()).mapRequiringInstanceType(linkCtx::type);
      } else {
        checkForType = EffesNativeObject.parseType(typeName);
      }
      return Operation.withIncementingPc(s -> {
        EffesRef<?> item = topItem.apply(s);
        BaseEffesType type = item.type();
        boolean rightType = type.equals(checkForType);
        if (rightType) {
          ifMatched.accept(s, item);
        }
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
