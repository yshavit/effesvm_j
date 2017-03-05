package com.yuvalshavit.effesvm.ops;

import static com.yuvalshavit.effesvm.util.LambdaHelpers.consumeAndReturn;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;

import com.yuvalshavit.effesvm.load.LinkContext;
import com.yuvalshavit.effesvm.runtime.EffesState;
import com.yuvalshavit.effesvm.runtime.PcMove;

public class OperationFactories {
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
        if (!(Operation.class.isAssignableFrom(retType) || UnlinkedOperation.class.isAssignableFrom(retType))) {
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
    return new IllegalArgumentException(String.format("%s::%s must be public, static, takes only Strings (or a Strings vararg) and return an Operation",
      enclosingClass.getName(), method.getName()));
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
      String desc = String.format("%s %s (%s::%s)",
        opName,
        consumeAndReturn(new StringJoiner(" "), j -> strings.forEach(j::add)),
        method.getDeclaringClass().getName(),
        method.getName());
      UnlinkedOperation result;
      if (opRaw instanceof Operation) {
        Op opWithDesc = new Op(((Operation) opRaw), desc);
        result = ctx -> opWithDesc;
      } else if (opRaw instanceof LabelUnlinkedOperation) {
        result = ((LabelUnlinkedOperation) opRaw);
      } else if (opRaw instanceof UnlinkedOperation) {
        result = new UnlinkedOp((UnlinkedOperation) opRaw, desc);
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
    private final UnlinkedOperation delegate;
    private final String description;

    public UnlinkedOp(UnlinkedOperation delegate, String description) {
      this.delegate = delegate;
      this.description = description;
    }

    @Override
    public Operation apply(LinkContext cxt) {
      Operation op = delegate.apply(cxt);
      return new Op(op, description);
    }

    @Override
    public String toString() {
      return description;
    }
  }

  private static class Op implements Operation {
    private final Operation delegate;
    private final String description;

    Op(Operation delegate, String description) {
      this.delegate = delegate;
      this.description = description;
    }

    @Override
    public PcMove apply(EffesState state) {
      return delegate.apply(state);
    }

    @Override
    public String toString() {
      return description;
    }
  }
}
