package com.yuvalshavit.effesvm.runtime;

public interface EffesRefVisitor {
  void start(BaseEffesType type);
  void attributePrimitive(String name, Object value);
  void attribute(String name, EffesRef<?> value);
  void end();
}
