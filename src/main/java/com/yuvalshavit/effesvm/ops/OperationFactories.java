package com.yuvalshavit.effesvm.ops;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.common.base.Joiner;
import com.yuvalshavit.effesvm.runtime.EffesState;
import com.yuvalshavit.effesvm.runtime.OpContext;
import com.yuvalshavit.effesvm.runtime.PcMove;

public class OperationFactories {
  private OperationFactories() {}

  public static Function<String,ReflectiveOperationBuilder> inClass(Class<?> enclosingClass) {
    Map<String,ReflectiveOperationBuilder> map = new HashMap<>();
    buildInto(map, enclosingClass);
    return Collections.unmodifiableMap(map)::get;
  }

  private static void buildInto(Map<String,ReflectiveOperationBuilder> map, Class<?> enclosingClass) {
    boolean classConfirmedToBePublicStatic = false;
    for (Method method : enclosingClass.getDeclaredMethods()) {
      OperationFactory factoryAnnotation = method.getAnnotation(OperationFactory.class);
      if (factoryAnnotation != null) {
        if (!classConfirmedToBePublicStatic) {
          // Do this check lazily, so that we don't error out on anonymous helper classes, interfaces, etc
          classConfirmedToBePublicStatic = classIsPublicStatic(enclosingClass);
          if (!classConfirmedToBePublicStatic) {
            throw new IllegalArgumentException("can only build methods from public static classes: " + enclosingClass.getName());
          }
        }
        if (!(isPublicStatic(method.getModifiers()) && Operation.class.isAssignableFrom(method.getReturnType()))) {
          throw notAValidFactoryMethod(enclosingClass, method);
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
          Class<?> parameterType = parameterTypes[i];
          if (!(String.class.equals(parameterType) || (i + 1 == parameterTypes.length && parameterType.equals(String[].class)))) {
            throw notAValidFactoryMethod(enclosingClass, method);
          }
        }
        String opName = factoryAnnotation.value();
        if (map.containsKey(opName)) {
          throw new IllegalArgumentException(String.format("%s already defined as %s", opName, map.get(opName)));
        }
        map.put(opName, new ReflectiveOperationBuilder(opName, method));
      }
    }

    for (Class<?> nested : enclosingClass.getDeclaredClasses()) {
      buildInto(map, nested);
    }
    Class<?> superclass = enclosingClass.getSuperclass();
    if (superclass != null && !Object.class.equals(superclass)) {
      buildInto(map, superclass);
    }
  }

  private static boolean classIsPublicStatic(Class<?> clazz) {
    int classModifiers = clazz.getModifiers();
    if (clazz.getEnclosingClass() == null) {
      Class<?> clazzSuper = clazz.getSuperclass();
      if (clazzSuper == null || classIsPublicStatic(clazzSuper)) {
        classModifiers |= Modifier.STATIC;
      }
    }
    return isPublicStatic(classModifiers);
  }

  private static boolean isPublicStatic(int modifiers) {
    return Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers);
  }

  private static IllegalArgumentException notAValidFactoryMethod(Class<?> enclosingClass, Method method) {
    return new IllegalArgumentException(String.format("%s::%s must be public, static, takes only Strings (or a Strings vararg) and return an Operation",
      enclosingClass.getName(), method.getName()));
  }

  public static class ReflectiveOperationBuilder implements Function<List<String>,Operation> {
    private final String opName;
    private final Method method;
    private final int nStringArgs;
    private final boolean hasStringArrayArg;

    private ReflectiveOperationBuilder(String opName, Method method) {
      this.opName = opName;
      this.method = method;
      int totalArgs = method.getParameterCount();
      hasStringArrayArg = totalArgs > 0 && String[].class.equals(method.getParameterTypes()[totalArgs - 1]);
      nStringArgs = hasStringArrayArg ? (totalArgs - 1) : totalArgs;
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
      if (nIncomingStrings > nStringArgs && !hasStringArrayArg) {
        throw new IllegalArgumentException(String.format("%s requires exactly %d string%s", opName, nStringArgs, nStringArgs == 1 ? "" : "s"));
      }
      Object[] reflectionArgs = new Object[nStringArgs + (hasStringArrayArg ? 1 : 0)];
      Iterator<String> stringsIter = strings.iterator();
      for (int i = 0; i < nStringArgs; ++i) {
        reflectionArgs[i] = stringsIter.next();
      }
      if (hasStringArrayArg) {
        int nVarArgs = nIncomingStrings - nStringArgs;
        String[] varArg = new String[nVarArgs];
        for (int i = 0; i < nVarArgs; ++i) {
          varArg[i] = stringsIter.next();
        }
        reflectionArgs[nStringArgs] = varArg;
      }
      Operation op;
      try {
        op = (Operation) method.invoke(null, reflectionArgs);
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

    public Op(Operation delegate, String description) {
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
