package com.yuvalshavit.effesvm.load;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.ops.OperationFactories;

public class Parser {
  private final Function<String,OperationFactories.ReflectiveOperationBuilder> opsFactories;

  public Parser(Function<String,OperationFactories.ReflectiveOperationBuilder> opsFactories) {
    this.opsFactories = opsFactories;
  }

  public Operation opLine(String line) {
    return opLine(line, (op, args) -> {
      OperationFactories.ReflectiveOperationBuilder builder = opsFactories.apply(op);
      if (builder == null) {
        throw new EffesLoadExeption("no such opcode: " + op);
      }
      return builder.apply(args);
    });
  }

  private <T> T opLine(String line, BiFunction<String,List<String>,T> handler) {
    // TODO quoting, etc
    String[] split = line.split(" ");
    String op = split[0];
    List<String> args = (split.length == 1)
      ? Collections.emptyList()
      : Arrays.asList(split).subList(1, split.length);
    return handler.apply(op, args);
  }
}
