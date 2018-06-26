package com.yuvalshavit.effesvm.ops;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.load.LinkContext;
import com.yuvalshavit.effesvm.runtime.EffesState;
import com.yuvalshavit.effesvm.runtime.PcMove;
import com.yuvalshavit.effesvm.util.AtMostOne;

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
      OperationFactory factoryAnnotation = findAnnotation(method, OperationFactory.class);
      if (factoryAnnotation != null) {
        Class<?> retType = method.getReturnType();
        if (retType != Void.TYPE) {
          throw notAValidFactoryMethod(enclosingClass_, method);
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 0 || ! parameterTypes[0].isAssignableFrom(OpBuilder.class)) {
          throw notAValidFactoryMethod(enclosingClass_, method);
        }
        for (int i = 1; i < parameterTypes.length; i++) {
          Class<?> parameterType = parameterTypes[i];
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

  private static <T extends Annotation> T findAnnotation(Method method, Class<T> annotationClass) {
    AtMostOne<T> annotation = new AtMostOne<>();
    annotation.accept(method.getAnnotation(annotationClass));
    // look on all interfaces up the class hierarchy
    Set<Class<?>> interfaces = new HashSet<>();
    for (Class<?> cls = method.getDeclaringClass(); cls != null; cls = cls.getSuperclass()) {
      Collections.addAll(interfaces, cls.getInterfaces());
    }
    for (Class<?> oneInterface : interfaces) {
      try {
        Method interfaceMethod = oneInterface.getMethod(method.getName(), method.getParameterTypes());
        annotation.accept(interfaceMethod.getAnnotation(annotationClass));
      } catch (NoSuchMethodException e) {
        // that's fine
      }
    }
    return annotation.get();
  }

  private static IllegalArgumentException notAValidFactoryMethod(Class<?> enclosingClass, Method method) {
    return new IllegalArgumentException(String.format(
      "%s::%s must be public, take an OpBuilder and then only Strings (or a Strings vararg) and return void",
      enclosingClass.getName(),
      method.getName()));
  }

  public static class ReflectiveOperationBuilder {
    private final String opName;
    private final Object suiteInstance;
    private final Method method;
    private final int nStringArgs;

    private ReflectiveOperationBuilder(String opName, Object suiteInstance, Method method) {
      this.opName = opName;
      this.suiteInstance = suiteInstance;
      this.method = method;
      nStringArgs = method.getParameterCount() - 1;
    }

    public UnlinkedOperation build(EffesModule.Id module, int lineNumber, String... strings) {
      return apply(module, lineNumber, Arrays.asList(strings));
    }

    public UnlinkedOperation apply(EffesModule.Id module, int lineNumber, List<String> strings) {
      int nIncomingStrings = strings.size();
      if (nIncomingStrings < nStringArgs) {
        throw new IllegalArgumentException(String.format("%s requires at least %d string%s", opName, nStringArgs, nStringArgs == 1 ? "" : "s"));
      }
      if (nIncomingStrings > nStringArgs) {
        throw new IllegalArgumentException(String.format("%s requires exactly %d string%s", opName, nStringArgs, nStringArgs == 1 ? "" : "s"));
      }

      Object[] reflectionArgs = new Object[nStringArgs + 1];
      Iterator<String> stringsIter = strings.iterator();
      OpInfo opInfo = new OpInfo(module, opName, strings, lineNumber + 1); // lineNumber is 0-indexed, we want 1-indexed for easier reading
      UnlikedOperationOpBuilder opBuilder = new UnlikedOperationOpBuilder(opInfo);
      reflectionArgs[0] = opBuilder;
      for (int i = 0; i < nStringArgs; ++i) {
        reflectionArgs[i+1] = stringsIter.next();
      }
      try {
        method.invoke(suiteInstance, reflectionArgs);
      } catch (Exception e) {
        throw new IllegalArgumentException("couldn't invoke " + method, e);
      }
      return opBuilder.get();
    }

    @Override
    public String toString() {
      return String.format("%s from %s::%s", opName, method.getDeclaringClass().getName(), method.getName());
    }
  }

  private static class UnlikedOperationOpBuilder implements OpBuilder {
    private final OpInfo opInfo;
    UnlinkedOperation result;

    public UnlikedOperationOpBuilder(OpInfo opInfo) {
      this.opInfo = opInfo;
    }

    void set(UnlinkedOperation result) {
      if (this.result != null) {
        throw new IllegalStateException("already created: " + result);
      }
      this.result = result;
    }

    UnlinkedOperation get() {
      if (result == null) {
        throw new IllegalStateException("hasn't been created");
      }
      return result;
    }

    @Override
    public void build(Operation.Body body) {
      Op opWithDesc = new Op(body, opInfo);
      set(ctx -> opWithDesc);
    }

    @Override
    public void build(UnlinkedOperation.Body body) {
      set(new UnlinkedOp(body, opInfo));
    }

    @Override
    public void build(LabelUnlinkedOperation.Body body) {
      String label = body.get();
      set(new LabelUnlinkedOperation(label, () -> new Op(Operation.withIncementingPc(s -> s.seeLabel(label)), opInfo)));
    }

    @Override
    public void build(VarUnlinkedOperation.Body body) {
      set(new VarUnlinkedOperation(body.varIndex(), () -> new Op(Operation.withIncementingPc(body.handler()), opInfo)));
    }
  }

  private static class UnlinkedOp implements UnlinkedOperation {
    private final OpInfo info;
    private final UnlinkedOperation.Body body;

    public UnlinkedOp(UnlinkedOperation.Body body, OpInfo info) {
      this.body = body;
      this.info = info;
    }

    @Override
    public Operation apply(LinkContext cxt) {
      Operation.Body op = body.apply(cxt);
      return new Op(op, info);
    }

    @Override
    public String toString() {
      return info.toString();
    }
  }

  private static class Op implements Operation {
    private final OpInfo info;
    private final Operation.Body body;

    Op(Operation.Body body, OpInfo info) {
      this.body = body;
      this.info = info;
    }

    @Override
    public OpInfo info() {
      return info;
    }

    @Override
    public PcMove apply(EffesState state) {
      return body.apply(state);
    }

    @Override
    public String toString() {
      return info.toString();
    }
  }
}
