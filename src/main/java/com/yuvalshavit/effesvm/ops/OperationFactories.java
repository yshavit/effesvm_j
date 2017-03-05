package com.yuvalshavit.effesvm.ops;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.yuvalshavit.effesvm.load.LinkContext;
import com.yuvalshavit.effesvm.runtime.EffesState;
import com.yuvalshavit.effesvm.runtime.PcMove;
import com.yuvalshavit.effesvm.util.LambdaHelpers;

public class OperationFactories {
  private static final List<Class<?>> allowedReturnTypes = Arrays.asList(Operation.Body.class, UnlinkedOperation.Body.class, LabelUnlinkedOperation.Body.class);

  private OperationFactories() {}

  public static Function<String,ReflectiveOperationBuilder> fromInstance(Object suiteInstance) {
    Map<String,ReflectiveOperationBuilder> map = new HashMap<>();
    buildInto(map, suiteInstance);
    return Collections.unmodifiableMap(map)::get;
  }

  private static void buildInto(Map<String,ReflectiveOperationBuilder> map, Object suiteInstance) {
    Class<?> enclosingClass_ = suiteInstance.getClass();
    for (Method method : enclosingClass_.getMethods()) {
      OperationFactory factoryAnnotation = method.getAnnotation(OperationFactory.class);
      if (factoryAnnotation != null) {
        Class<?> retType = method.getReturnType();
        if (allowedReturnTypes.stream().noneMatch(t -> t.isAssignableFrom(retType))) {
          throw notAValidFactoryMethod(enclosingClass_, method);
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (Class<?> parameterType : parameterTypes) {
          if (!(String.class.equals(parameterType))) {
            throw notAValidFactoryMethod(enclosingClass_, method);
          }
        }
        String opName = factoryAnnotation.value();
        if (map.containsKey(opName)) {
          throw new IllegalArgumentException(String.format("%s already defined as %s", opName, map.get(opName)));
        }
        map.put(opName, new ReflectiveOperationBuilder(opName, suiteInstance, method));
      }
    }
  }

  private static IllegalArgumentException notAValidFactoryMethod(Class<?> enclosingClass, Method method) {
    return new IllegalArgumentException(String.format(
      "%s::%s must be public, static, takes only Strings (or a Strings vararg) and return one of %s",
      enclosingClass.getName(),
      method.getName(),
      allowedReturnTypes.stream().map(Class::getName).collect(Collectors.joining(", "))));
  }

  public static class ReflectiveOperationBuilder implements Function<List<String>,UnlinkedOperation> {
    private final String opName;
    private final Object suiteInstance;
    private final Method method;
    private final int nStringArgs;

    private ReflectiveOperationBuilder(String opName, Object suiteInstance, Method method) {
      this.opName = opName;
      this.suiteInstance = suiteInstance;
      this.method = method;
      nStringArgs = method.getParameterCount();
    }

    public UnlinkedOperation build(String... strings) {
      return apply(Arrays.asList(strings));
    }

    @Override
    public UnlinkedOperation apply(List<String> strings) {
      int nIncomingStrings = strings.size();
      if (nIncomingStrings < nStringArgs) {
        throw new IllegalArgumentException(String.format("%s requires at least %d string%s", opName, nStringArgs, nStringArgs == 1 ? "" : "s"));
      }
      if (nIncomingStrings > nStringArgs) {
        throw new IllegalArgumentException(String.format("%s requires exactly %d string%s", opName, nStringArgs, nStringArgs == 1 ? "" : "s"));
      }
      Object[] reflectionArgs = new Object[nStringArgs];
      Iterator<String> stringsIter = strings.iterator();
      for (int i = 0; i < nStringArgs; ++i) {
        reflectionArgs[i] = stringsIter.next();
      }
      Object opRaw;
      try {
        opRaw = method.invoke(suiteInstance, reflectionArgs);
      } catch (Exception e) {
        throw new IllegalArgumentException("couldn't invoke " + method, e);
      }
      UnlinkedOperation result;
      if (opRaw instanceof Operation.Body) {
        Op opWithDesc = new Op(((Operation.Body) opRaw), new OpDesc(opName, strings));
        result = ctx -> opWithDesc;
      } else if (opRaw instanceof LabelUnlinkedOperation.Body) {
        String label = ((LabelUnlinkedOperation.Body) opRaw).get();
        result = new LabelUnlinkedOperation(label, () -> new Op(Operation.withIncementingPc(s -> s.seeLabel(label)), new OpDesc(opName, strings)));
      } else if (opRaw instanceof UnlinkedOperation.Body) {
        result = new UnlinkedOp((UnlinkedOperation.Body) opRaw, new OpDesc(opName, strings));
      }
      else {
        throw new RuntimeException("unexpected result: " + opRaw);
      }
      return result;
    }

    @Override
    public String toString() {
      return String.format("%s from %s::%s", opName, method.getDeclaringClass().getName(), method.getName());
    }
  }

  private static class UnlinkedOp implements UnlinkedOperation {
    private final OpDesc description;
    private final UnlinkedOperation.Body body;

    public UnlinkedOp(UnlinkedOperation.Body body, OpDesc description) {
      this.body = body;
      this.description = description;
    }

    @Override
    public Operation apply(LinkContext cxt) {
      Operation.Body op = body.apply(cxt);
      return new Op(op, description);
    }

    @Override
    public String toString() {
      return description.toString();
    }
  }

  private static class Op implements Operation {
    private final OpDesc description;
    private final Operation.Body body;

    Op(Operation.Body body, OpDesc description) {
      this.body = body;
      this.description = description;
    }

    @Override
    public String opcode() {
      return description.opcode;
    }

    @Override
    public List<String> arguments() {
      return description.arguments;
    }

    @Override
    public PcMove apply(EffesState state) {
      return body.apply(state);
    }

    @Override
    public String toString() {
      return description.toString();
    }
  }

  private static class OpDesc {
    final String opcode;
    final List<String> arguments;

    public OpDesc(String opcode, List<String> arguments) {
      this.opcode = opcode;
      this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
    }

    @Override
    public String toString() {
      return LambdaHelpers.consumeAndReturn(new StringJoiner(", ", opcode + " ", ""), j -> arguments.forEach(j::add)).toString();
    }
  }
}
