package com.yuvalshavit.effesvm.ops;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.common.base.Joiner;
import com.yuvalshavit.effesvm.runtime.OpContext;
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
        if (!Operation.class.isAssignableFrom(method.getReturnType())) {
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
    // TODO sanity check that there are no OperationFactory annotations on non-public methods (including in super classes) or nested classes
  }

  private static IllegalArgumentException notAValidFactoryMethod(Class<?> enclosingClass, Method method) {
    return new IllegalArgumentException(String.format("%s::%s must be public, static, takes only Strings (or a Strings vararg) and return an Operation",
      enclosingClass.getName(), method.getName()));
  }

  public static class ReflectiveOperationBuilder implements Function<List<String>,Operation> {
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

    public Operation build(String... strings) {
      return apply(Arrays.asList(strings));
    }

    @Override
    public Operation apply(List<String> strings) {
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
      Operation op;
      try {
        op = (Operation) method.invoke(suiteInstance, reflectionArgs);
      } catch (Exception e) {
        throw new IllegalArgumentException("couldn't invoke " + method, e);
      }
      String desc = String.format("%s %s (%s::%s)", opName, Joiner.on(' ').join(strings), method.getDeclaringClass().getName(), method.getName());
      return new Op(op, desc);
    }

    @Override
    public String toString() {
      return String.format("%s from %s::%s", opName, method.getDeclaringClass().getName(), method.getName());
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
    public PcMove apply(OpContext context) {
      return delegate.apply(context);
    }

    @Override
    public String toString() {
      return description;
    }
  }
}
