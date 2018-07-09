package com.yuvalshavit.effesvm.ops;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.util.StringEscaper;

public class OpInfo implements Serializable {
  private final EffesModule.Id module;
  private final String opcode;
  private final List<String> arguments;
  private final int efctLineNumber;
  private final int sourceLineNumber;
  private final int sourcePositionInLine;

  public OpInfo(EffesModule.Id module, String opcode, List<String> arguments, int efctLineNumber, int sourceLineNumber, int sourcePositionInLine) {
    this.module = module;
    this.opcode = opcode;
    this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
    this.efctLineNumber = efctLineNumber;
    this.sourceLineNumber = sourceLineNumber;
    this.sourcePositionInLine = sourcePositionInLine;
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
    return efctLineNumber;
  }

  public int sourceLineNumberIndexedAt0() {
    return sourceLineNumber - 1;
  }

  public int sourcePositionInLine() {
    return sourcePositionInLine;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('#').append(efctLineNumber).append(' ');
    if (sourceLineNumber >= 0) {
      sb.append('(').append(sourceLineNumber).append(':').append(sourcePositionInLine).append(") ");
    }
    sb.append(opcode);
    return arguments.stream().map(StringEscaper::escape).collect(Collectors.joining(" ", sb, ""));
  }
}
