package com.yuvalshavit.effesvm.runtime;

import java.util.Arrays;
import java.util.StringJoiner;
import java.util.stream.IntStream;

import com.google.common.base.Joiner;

public class EffesObject {
  private final EffesType type;
  private final Object[] args;

  public EffesObject(EffesType type, Object[] args) {
    this.type = type;
    this.args = Arrays.copyOf(args, args.length);
    if (args.length != type.nArgs()) {
      throw new EffesRuntimeException(String.format("wrong number of arguments: expected %d but saw %d", type.nArgs(), args.length));
    }
  }

  public EffesType type() {
    return type;
  }

  public Object getArg(String name) {
    return args[type.argIndex(name)];
  }

  @Override
  public String toString() {
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
