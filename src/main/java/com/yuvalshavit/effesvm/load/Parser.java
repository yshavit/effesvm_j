package com.yuvalshavit.effesvm.load;

import java.util.*;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import com.google.common.base.Preconditions;
import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.ops.OperationFactories;
import com.yuvalshavit.effesvm.runtime.EffesFunction;
import com.yuvalshavit.effesvm.runtime.EffesModule;

public class Parser {
  public static final String EFCT_0_HEADER = "efct 0";
  private final Function<String,OperationFactories.ReflectiveOperationBuilder> opsFactories;

  public Parser(Function<String,OperationFactories.ReflectiveOperationBuilder> opsFactories) {
    this.opsFactories = opsFactories;
  }

  public EffesModule parse(Iterator<String> lines) {
    if (!lines.hasNext()) {
      return new EffesModule(Collections.emptyMap());
    }
    if (!lines.next().equals(EFCT_0_HEADER)) {
      throw new IllegalArgumentException("file must start with \"" + EFCT_0_HEADER + "\"");
    }
    Iterator<Line> tokenizedLines = StreamSupport.stream(Spliterators.spliteratorUnknownSize(lines, Spliterator.ORDERED), false)
      .map(String::trim)
      .filter(l -> !l.startsWith("#"))
      .map(Line::new)
      .iterator();

    Map<EffesFunction.Id,EffesFunction> functions = new HashMap<>();
    while (tokenizedLines.hasNext()) {
      Line line = tokenizedLines.next();
      if (line.isEmpty()) {
        continue;
      }
      switch (line.get(0, "declaration type")) {
        case "FUNC":
          String className = line.get(1, "classname");
          String functionName = line.get(2, "functionname");
          EffesFunction parsedFunction = parseFunction(
            tokenizedLines,
            className,
            functionName,
            line.get(3, "nGenerics", Integer::parseInt),
            line.get(4, "nLocal", Integer::parseInt),
            line.get(5, "nArgs", Integer::parseInt));
          functions.put(new EffesFunction.Id(className, functionName), parsedFunction);
          break;
        default:
          throw new IllegalArgumentException("unrecognized declaration type");
      }
    }
    return new EffesModule(Collections.unmodifiableMap(functions));
  }

  private EffesFunction parseFunction(Iterator<Line> lines, String className, String functionName, int nGenerics, int nLocal, int nArgs) {
    Preconditions.checkArgument(nGenerics == 0, "nGenerics");
    Preconditions.checkArgument(nLocal >= 0, "nLocal: " + nLocal);
    Preconditions.checkArgument(nArgs >= 0, "nArgs: " + nArgs);

    Preconditions.checkArgument(className.equals(EffesFunction.MODULE_CLASSNAME), "classname: " + className); // no instance methods yet!

    List<Operation> ops = new ArrayList<>();
    while (lines.hasNext()) {
      Line line = lines.next();
      if (line.isEmpty()) {
        break;
      }
      String opcode = line.get(0, "opcode");
      OperationFactories.ReflectiveOperationBuilder opBuilder = opsFactories.apply(opcode);
      // TODO any validation we want to do? e.g. that we never fetch out of range args or locals?
      if (opBuilder == null) {
        throw new EffesLoadExeption("no such op: " + opcode);
      }
      Operation op = opBuilder.build(line.tokensFrom(1));
      ops.add(op);
    }
    return new EffesFunction(functionName, nLocal, nArgs, ops.toArray(new Operation[0]));
  }

  private static class Line {
    private final String[] tokens;

    Line(String line) {
      line = line.trim();
      this.tokens = line.isEmpty() ? null : line.split(" +"); // TODO better tokenization!
    }

    boolean isEmpty() {
      return tokens == null;
    }

    <T> T get(int idx, String attrDescription, Function<String,T> tokenParser) {
      String tokenText;
      try {
        tokenText = tokens[idx];
      } catch (ArrayIndexOutOfBoundsException e) {
        throw new IllegalArgumentException("missing " + attrDescription);
      }
      try {
        return tokenParser.apply(tokenText);
      } catch (Exception e) {
        throw new IllegalArgumentException("invalid " + attrDescription, e);
      }
    }

    String get(int idx, String attrDescription) {
      return get(idx, attrDescription, Function.identity());
    }

    String[] tokensFrom(int startingIndex) {
      return Arrays.copyOfRange(tokens, startingIndex, tokens.length);
    }

    @Override
    public String toString() {
      return Arrays.toString(tokens);
    }
  }
}
