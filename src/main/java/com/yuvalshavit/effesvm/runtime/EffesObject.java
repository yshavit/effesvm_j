package com.yuvalshavit.effesvm.runtime;

import java.util.Arrays;
import java.util.StringJoiner;
import java.util.stream.IntStream;

public class EffesObject extends EffesRef<EffesType> {
  private final EffesRef<?>[] args;

  public EffesObject(EffesType type, EffesRef<?>[] args) {
    super(type);
    this.args = Arrays.copyOf(args, args.length);
    if (args.length != type.nArgs()) {
      throw new EffesRuntimeException(String.format("wrong number of arguments: expected %d but saw %d", type.nArgs(), args.length));
    }
  }

  public EffesRef<?> getArg(String name) {
    return args[type().argIndex(name)];
  }

  @Override
  public String toString() {
    EffesType type = type();
    if (args.length == 0) {
      return type.toString();
    } else {
      StringJoiner joiner = new StringJoiner(", ", type + "{", "}");
      IntStream.range(0, args.length)
        .mapToObj(i -> String.format("%s=%s", type.argAt(i), args[i]))
        .forEachOrdered(joiner::add);
      return joiner.toString();
    }
  }
}
