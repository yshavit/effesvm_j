package com.yuvalshavit.effesvm.ops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.util.LambdaHelpers;

public class OpInfo {
  private final EffesModule.Id module;
  private final String opcode;
  private final List<String> arguments;
  private final int lineNumber;

  public OpInfo(EffesModule.Id module, String opcode, List<String> arguments, int lineNumber) {
    this.module = module;
    this.opcode = opcode;
    this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
    this.lineNumber = lineNumber;
  }

  public EffesModule.Id module() {
    return module;
  }

  public String opcode() {
    return opcode;
  }

  public List<String> arguments() {
    return arguments;
  }

  public int lineNumber() {
    return lineNumber;
  }

  @Override
  public String toString() {
    String lineNumAndOpcode = String.format("#%d %s ", lineNumber, opcode);
    return LambdaHelpers.consumeAndReturn(new StringJoiner(", ", lineNumAndOpcode, ""), j -> arguments.forEach(j::add)).toString();
  }
}
