package com.yuvalshavit.effesvm.runtime;

import java.util.Arrays;

public class EffesObject extends EffesRef<EffesType> {
  private final EffesRef<?>[] args;

  public EffesObject(EffesType type, EffesRef<?>[] args) {
    super(type);
    this.args = Arrays.copyOf(args, args.length);
    if (args.length != type.nArgs()) {
      throw new EffesRuntimeException(String.format("wrong number of arguments: expected %d but saw %d", type.nArgs(), args.length));
    }
  }

  public EffesRef<?> getArgAt(int idx) {
    return args[idx];
  }

  public void storeArgTo(int idx, EffesRef<?> value) {
    args[idx] = value;
  }

  @Override
  protected void visitAttrs(EffesRefVisitor visitor) {
    for (int i = 0; i < args.length; ++i) {
      visitor.attribute(type().argAt(i), getArgAt(i));
    }
  }
}
