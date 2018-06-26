package com.yuvalshavit.effesvm.runtime;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.yuvalshavit.effesvm.load.EfctScope;
import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesLinkException;
import com.yuvalshavit.effesvm.load.EffesLoadException;
import com.yuvalshavit.effesvm.load.LinkContext;
import com.yuvalshavit.effesvm.ops.LabelUnlinkedOperation;
import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.ops.OpBuilder;
import com.yuvalshavit.effesvm.ops.UnlinkedOperation;
import com.yuvalshavit.effesvm.ops.VarUnlinkedOperation;

public class EffesOpsImpl implements EffesOps<OpBuilder> {

  private final EffesIo io;

  public EffesOpsImpl(EffesIo io) {
    this.io = io;
  }

  @Override
  public void createArray(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      int size = popInt(s);
      EffesRef<?> arr = new EffesNativeObject.EffesArray(size);
      s.push(arr);
    });
  }

  @Override
  public void pushInt(OpBuilder builder, String value) {
    int asInt = Integer.parseInt(value);
    EffesNativeObject.EffesInteger obj = EffesNativeObject.forInt(asInt);
    builder.withIncementingPc(s -> s.push(obj));
  }

  @Override
  public void pop(OpBuilder builder) {
    builder.withIncementingPc(EffesState::pop);
  }

  @Override
  public void copy(OpBuilder builder) {
    builder.withIncementingPc(s -> s.push(s.peek(0)));
  }

  @Override
  public void pvar(OpBuilder builder, String n) {
    int idx = nonNegative(n);
    builder.build(VarUnlinkedOperation.pushVar(idx));
  }

  @Override
  public void svar(OpBuilder builder, String n) {
    int idx = nonNegative(n);
    builder.build(VarUnlinkedOperation.popToVar(idx));
  }

  @Override
  public void Svar(OpBuilder builder, String n) {
    int idx = nonNegative(n);
    builder.build(VarUnlinkedOperation.copyToVar(idx));
  }

  @Override
  public void gotoAbs(OpBuilder builder, String n) {
    UnlinkedOperation.Body body = linkCtx -> {
      PcMove pcMove = pcMoveTo(linkCtx, n);
      return s -> pcMove;
    };
    builder.build(body);
  }

  @Override
  public void gotoIf(OpBuilder builder, String n) {
    buildGoif(builder, n, Boolean::booleanValue);
  }

  @Override
  public void gotoIfNot(OpBuilder builder, String n) {
    buildGoif(builder, n, b -> !b);
  }

  @Override
  public void rtrn(OpBuilder builder) {
    Operation.Body body = s -> {
      s.closeFrame();
      return PcMove.next();
    };
    builder.build(body);
  }

  @Override
  public void type(OpBuilder builder, String typeName) {
    typeBuilder(builder, typeName, EffesState::pop, (s, i) -> {});
  }

  @Override
  public void typp(OpBuilder builder, String typeName) {
    typeBuilder(builder, typeName, s -> s.peek(0), (s, i) -> {});
  }

  @Override
  public void typf(OpBuilder builder, String typeName) {
    typeBuilder(builder, typeName, EffesState::pop, EffesState::push);
  }

  @Override
  public void pushField(OpBuilder builder, String typeName, String fieldName) {
    pushFieldOperation(builder, typeName, fieldName, EffesState::pop);
  }

  @Override
  public void PushField(OpBuilder builder, String typeName, String fieldName) {
    pushFieldOperation(builder, typeName, fieldName, s -> s.peek(0));
  }

  private void pushFieldOperation(OpBuilder builder, String typeName, String fieldName, Function<EffesState,EffesRef<?>> getTop) {
    fieldOperation(builder, typeName, fieldName, (type, fieldIndex) -> s -> {
      EffesObject obj = (EffesObject) getTop.apply(s);
      if (!obj.type().equals(type)) {
        throw new EffesRuntimeException(String.format("can't fetch %s.%s on an object of type %s", type.argAt(fieldIndex), fieldName, obj.type()));
      }
      EffesRef<?> arg = obj.getArgAt(fieldIndex);
      s.push(arg);
    });
  }

  @Override
  public void storeField(OpBuilder builder, String typeName, String fieldName) {
    fieldOperation(builder, typeName, fieldName, (type, fieldIndex) -> s -> {
      EffesObject obj = (EffesObject) s.pop();
      EffesRef<?> newFieldValue = s.pop();
      if (!obj.type().equals(type)) {
        throw new EffesRuntimeException(String.format("can't fetch %s.%s on an object of type %s", type.argAt(fieldIndex), fieldName, obj.type()));
      }
      obj.storeArgTo(fieldIndex, newFieldValue);
    });
  }

  @Override
  public void arrayStore(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      EffesRef<?> value = s.pop();
      int idx = popInt(s);
      EffesNativeObject.EffesArray arr = (EffesNativeObject.EffesArray) s.pop();
      arr.store(idx, value);
    });
  }

  @Override
  public void arrayGet(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      int idx = popInt(s);
      EffesNativeObject.EffesArray arr = (EffesNativeObject.EffesArray) s.pop();
      EffesRef<?> value = arr.get(idx);
      s.push(value);
    });
  }

  @Override
  public void arrayLen(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      EffesNativeObject.EffesArray arr = (EffesNativeObject.EffesArray) s.pop();
      s.push(EffesNativeObject.forInt(arr.length()));
    });
  }

  @Override
  public void nativeToString(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      EffesRef<?> top = s.pop();
      s.push(EffesNativeObject.forString(top.toString()));
    });
  }

  @Override
  public void nativeToStringPretty(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      EffesRef<?> top = s.pop();
      String pretty = top.visit(new EffesRefFormats.Pretty()).toString();
      s.push(EffesNativeObject.forString(pretty));
    });
  }

  @Override
  public void parseInt(OpBuilder builder) {
    builder.withIncementingPc(s -> {
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
  public void lt(OpBuilder builder) {
    intCmp(builder, (l, r) -> (l < r));
  }

  @Override
  public void le(OpBuilder builder) {
    intCmp(builder, (l, r) -> (l <= r));
  }

  @Override
  public void eq(OpBuilder builder) {
    intCmp(builder, (l, r) -> (l == r));
  }

  @Override
  public void ne(OpBuilder builder) {
    intCmp(builder, (l, r) -> (l != r));
  }

  @Override
  public void ge(OpBuilder builder) {
    intCmp(builder, (l, r) -> (l >= r));
  }

  @Override
  public void gt(OpBuilder builder) {
    intCmp(builder, (l, r) -> (l > r));
  }

  @Override
  public void call(OpBuilder builder, String scopeSpecifier, String functionName) {
    UnlinkedOperation.Body unlinked = linkCtx -> {
      EffesFunctionId functionId = EffesFunctionId.parse(scopeSpecifier, functionName, linkCtx.currentModule());
      if (functionId.isConstructor()) {
        // constructor
        EffesType type = functionId.getScope().mapRequiringInstanceType(linkCtx::type);
        return runCtx -> {
          EffesRef<?>[] args = new EffesRef<?>[type.nArgs()];
          for (int i = 0; i < args.length ; ++i) {
            args[i] = runCtx.pop();
          }
          EffesObject obj = new EffesObject(type, args);
          runCtx.push(obj);
          return PcMove.next();
        };
      } else {
        // non-constructor function
        EffesFunction f = linkCtx.getFunctionInfo(functionId);
        PcMove pcMove;
        try {
          pcMove = PcMove.firstCallIn(f);
        } catch (IllegalArgumentException e) {
          throw new NoSuchElementException("method " + functionId);
        }
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
          c.openFrame(nArgs, f.hasRv(), f.nVars());
          return pcMove;
        };
      }
    };
    builder.build(unlinked);
  }

  @Override
  public void iAdd(OpBuilder builder) {
    intArith(builder, (l, r) -> (l + r));
  }

  @Override
  public void iSub(OpBuilder builder) {
    intArith(builder, (l, r) -> (l - r));
  }

  @Override
  public void iMul(OpBuilder builder) {
    intArith(builder, (l, r) -> (l * r));
  }

  @Override
  public void iDiv(OpBuilder builder) {
    intArith(builder, (l, r) -> (l / r));
  }

  @Override
  public void debugPrint(OpBuilder builder) {
    debugPrint(builder, () -> new EffesRefFormats.Inline(true));
  }

  @Override
  public void debugPrintPretty(OpBuilder builder) {
    debugPrint(builder, EffesRefFormats.Pretty::new);
  }

  private void debugPrint(OpBuilder builder, Supplier<EffesRefVisitor> visitor) {
    builder.withIncementingPc(s -> {
      String line = s.getLocalStackSize() > 0
        ? String.valueOf(s.peek(0).visit(visitor.get()).toString())
        : "<the local stack for this frame is empty>";
      EffesOutput err = io.err();
      err.write(line);
      err.write("\n");
    });
  }

  @Override
  public void bool(OpBuilder builder, String sValue) {
    boolean bValue;
    if ("True".equals(sValue)) {
      bValue = true;
    } else if ("False".equals(sValue)) {
      bValue = false;
    } else {
      throw new EffesLoadException("invalid argument: " + sValue);
    }
    EffesNativeObject eValue = EffesNativeObject.forBoolean(bValue);
    builder.withIncementingPc(s -> s.push(eValue));
  }

  @Override
  public void negate(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      boolean value = popBoolean(s);
      EffesNativeObject.EffesBoolean push = EffesNativeObject.forBoolean(!value);
      s.push(push);
    });
  }

  @Override
  public void and(OpBuilder builder) {
    booleanOp(builder, Boolean::logicalAnd);
  }

  @Override
  public void or(OpBuilder builder) {
    booleanOp(builder, Boolean::logicalOr);
  }

  @Override
  public void xor(OpBuilder builder) {
    booleanOp(builder, Boolean::logicalXor);
  }

  @Override
  public void matchIndexedGroup(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      int idx = popInt(s);
      EffesNativeObject.EffesMatch match = (EffesNativeObject.EffesMatch) s.pop();
      s.push(match.group(idx));
    });
  }

  @Override
  public void matchNamedGroup(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      String name = popString(s);
      EffesNativeObject.EffesMatch match = (EffesNativeObject.EffesMatch) s.pop();
      s.push(match.group(name));
    });
  }

  @Override
  public void matchGroupCount(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      EffesNativeObject.EffesMatch match = (EffesNativeObject.EffesMatch) s.pop();
      s.push(match.groupCount());
    });
  }

  @Override
  public void matchTail(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      EffesNativeObject.EffesMatch match = (EffesNativeObject.EffesMatch) s.pop();
      s.push(match.tail());
    });
  }

  @Override
  public void strPush(OpBuilder builder, String value) {
    EffesNativeObject eStr = EffesNativeObject.forString(value);
    builder.withIncementingPc(s -> s.push(eStr));
  }

  @Override
  public void stringLen(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      String str = popString(s);
      int len = str.codePointCount(0, str.length());
      s.push(EffesNativeObject.forInt(len));
    });
  }

  @Override
  public void stringRegex(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      String patternStr = popString(s);
      String lookIn = popString(s);
      s.push(EffesNativeObject.tryMatch(lookIn, patternStr));
    });
  }

  @Override
  public void sout(OpBuilder builder) {
    builder.withIncementingPc(s -> io.out().write(popString(s)));
  }

  @Override
  public void concat(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      EffesNativeObject.EffesString second = (EffesNativeObject.EffesString) s.pop();
      EffesNativeObject.EffesString first = (EffesNativeObject.EffesString) s.pop();
      EffesNativeObject.EffesString result = EffesNativeObject.forString(first.value + second.value);
      s.push(result);
    });
  }

  @Override
  public void stdin(OpBuilder builder) {
    builder.withIncementingPc(s -> s.push(new EffesNativeObject.EffesStreamIn(io.in())));
  }

  @Override
  public void stdout(OpBuilder builder) {
    builder.withIncementingPc(s -> s.push(new EffesNativeObject.EffesStreamOut(io.out())));
  }

  @Override
  public void writeFile(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      String fileName = popString(s);
      PrintStream printer = new PrintStream(io.writeFile(fileName));

      EffesNativeObject.EffesStreamOut stream = new EffesNativeObject.EffesStreamOut(new EffesOutput.FromWriter(printer, fileName));
      s.push(stream);
    });
  }

  @Override
  public void writeText(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      EffesNativeObject.EffesString text = (EffesNativeObject.EffesString) s.pop();
      EffesNativeObject.EffesStreamOut streamOut = (EffesNativeObject.EffesStreamOut) s.pop();
      streamOut.get().write(text.value);
    });
  }

  @Override
  public void readFile(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      String fileName = popString(s);
      BufferedReader reader = new BufferedReader(new InputStreamReader(io.readFile(fileName), StandardCharsets.UTF_8));
      EffesNativeObject.EffesStreamIn stream = new EffesNativeObject.EffesStreamIn(new EffesInput.FromReader(reader, fileName));
      s.push(stream);
    });
  }

  @Override
  public void readLine(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      EffesNativeObject.EffesStreamIn streamIn = (EffesNativeObject.EffesStreamIn) s.pop();
      pushStringOrFalse(s, streamIn.get().readLine());
    });
  }

  @Override
  public void stdinLine(OpBuilder builder) {
    builder.withIncementingPc(s -> pushStringOrFalse(s, io.in().readLine()));
  }

  public void pushStringOrFalse(EffesState state, String stringOrNull) {
    state.push((stringOrNull == null) ? EffesNativeObject.EffesBoolean.FALSE : EffesNativeObject.forString(stringOrNull));
  }

  @Override
  public void stringBuilderAdd(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      EffesNativeObject.EffesString toAdd = (EffesNativeObject.EffesString) s.pop();
      EffesNativeObject.EffesStringBuilder sb = (EffesNativeObject.EffesStringBuilder) s.pop();
      String valueToAdd = Objects.requireNonNull(toAdd.value, "null value");
      sb.sb.append(valueToAdd);
    });
  }

  @Override
  public void stringBuilderGet(OpBuilder builder) {
    builder.withIncementingPc(s -> {
      EffesNativeObject.EffesStringBuilder sb = (EffesNativeObject.EffesStringBuilder) s.pop();
      s.push(EffesNativeObject.EffesString.forString(sb.getString()));
    });
  }

  @Override
  public void label(OpBuilder builder, String name) {
    builder.build(new LabelUnlinkedOperation.Body(name));
  }

  @Override
  public void sbld(OpBuilder builder) {
    builder.withIncementingPc(s -> s.push(new EffesNativeObject.EffesStringBuilder()));
  }

  @Override
  public void fail(OpBuilder builder, String message) {
    builder.build((Operation.Body) s -> {
      s.getStackTrace().forEach(System.err::println);
      System.err.flush();
      throw new EffesRuntimeException(message);
    });
  }

  private void buildGoif(OpBuilder builder, String loc, Predicate<Boolean> condition) {
    UnlinkedOperation.Body body = linkCtx -> {
      PcMove to = pcMoveTo(linkCtx, loc);
      return s -> {
        boolean top = ((EffesNativeObject.EffesBoolean) s.pop()).asBoolean();
        return condition.test(top) ? to : PcMove.next();
      };
    };
    builder.build(body);
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

  private void booleanOp(OpBuilder builder, BinaryOperator<Boolean> op) {
    builder.withIncementingPc(s -> {
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

  private void fieldOperation(OpBuilder builder, String typeName, String fieldName, FieldOperator op) {
    UnlinkedOperation.Body unlinked = linkContext -> EfctScope.parse(typeName, linkContext.currentModule()).mapRequiringInstanceType((m, t) -> {
        EffesType type = linkContext.type(m, t);
        int fieldIndex = type.argIndex(fieldName);
        return Operation.withIncementingPc(op.fieldOperation(type, fieldIndex));
      });
    builder.build(unlinked);
  }

  private void intArith(OpBuilder builder, IntBinaryOperator op) {
    builder.withIncementingPc(s -> {
      int rhs = popInt(s);
      int lhs = popInt(s);
      s.push(EffesNativeObject.forInt(op.applyAsInt(lhs, rhs)));
    });
  }

  private void intCmp(OpBuilder builder, IntCmp intCmp) {
    builder.withIncementingPc(s -> {
      int lhs = popInt(s);
      int rhs = popInt(s);
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

  private void typeBuilder(OpBuilder builder, String typeName, Function<EffesState,EffesRef<?>> topItem, BiConsumer<EffesState,EffesRef<?>> ifMatched) {
    UnlinkedOperation.Body unlinked = linkCtx -> {
      BaseEffesType checkForType;
      if (typeName.indexOf(':') >= 0) {
        checkForType = EfctScope.parse(typeName, linkCtx.currentModule()).mapRequiringInstanceType(linkCtx::type);
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
    builder.build(unlinked);
  }

  private interface FieldOperator {
    Consumer<EffesState> fieldOperation(EffesType type, int fieldIndex);
  }

  private interface IntCmp {
    boolean cmp(int lhs, int rhs);
  }
}
