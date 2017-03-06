package com.yuvalshavit.effesvm.ops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

import com.yuvalshavit.effesvm.util.LambdaHelpers;

public class OpInfo {
  private final String opcode;
  private final List<String> arguments;
  private final int lineNumber;

  public OpInfo(String opcode, List<String> arguments, int lineNumber) {
    this.opcode = opcode;
    this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
    this.lineNumber = lineNumber;
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
    return LambdaHelpers.consumeAndReturn(new StringJoiner(", ", opcode + " ", ""), j -> arguments.forEach(j::add)).toString();
  }
}
