package com.yuvalshavit.effesvm.load;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.ops.OperationFactories;
import com.yuvalshavit.effesvm.runtime.EffesFunction;
import com.yuvalshavit.effesvm.runtime.EffesModule;
import com.yuvalshavit.effesvm.runtime.EffesType;
import com.yuvalshavit.effesvm.util.SimpleTokenizer;

public class Parser {
  public static final String EFCT_0_HEADER = "efct 0";
  private final Function<String,OperationFactories.ReflectiveOperationBuilder> opsFactories;

  public Parser(Function<String,OperationFactories.ReflectiveOperationBuilder> opsFactories) {
    this.opsFactories = opsFactories;
  }

  public EffesModule parse(Iterator<String> lines) {
    if (!lines.hasNext()) {
      return new EffesModule(Collections.emptyMap(), Collections.emptyMap());
    }
    if (!lines.next().equals(EFCT_0_HEADER)) {
      throw new IllegalArgumentException("file must start with \"" + EFCT_0_HEADER + "\"");
    }
    Iterator<Line> tokenizedLines = Iterators.transform(lines, Line::new);

    Map<EffesFunction.Id,EffesFunction> functions = new HashMap<>();
    Map<String,EffesType> types = new HashMap<>();
    while (tokenizedLines.hasNext()) {
      Line line = tokenizedLines.next();
      if (line.isEmptyOrComment()) {
        continue;
      }
      String declarationType = line.get(0, "declaration type");
      switch (declarationType) {
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
          functions.put(parsedFunction.id(), parsedFunction);
          break;
        case "TYPE":
          EffesType type = parseType(line);
          types.put(type.name(), type);
          break;
        default:
          throw new IllegalArgumentException("unrecognized declaration type: " + declarationType);
      }
    }
    return new EffesModule(types, Collections.unmodifiableMap(functions));
  }

  private EffesFunction parseFunction(Iterator<Line> lines, String className, String functionName, int nGenerics, int nLocal, int nArgs) {
    Preconditions.checkArgument(nGenerics == 0, "nGenerics");
    Preconditions.checkArgument(nLocal >= 0, "nLocal: " + nLocal);
    Preconditions.checkArgument(nArgs >= 0, "nArgs: " + nArgs);

    List<Operation> ops = new ArrayList<>();
    while (lines.hasNext()) {
      Line line = lines.next();
      if (line.isEmpty()) {
        break;
      } else if (line.isEmptyOrComment()) {
        continue;
      }
      String opcode = line.get(0, "opcode");
      OperationFactories.ReflectiveOperationBuilder opBuilder = opsFactories.apply(opcode);
      // TODO any validation we want to do? e.g. that we never fetch out of range args or locals?
      if (opBuilder == null) {
        throw new EffesLoadException("no such op: " + opcode);
      }
      Operation op = opBuilder.build(line.tailTokens(1));
      ops.add(op);
    }
    return new EffesFunction(new EffesFunction.Id(className, functionName), nLocal, nArgs, ops.toArray(new Operation[0]));
  }

  private EffesType parseType(Line line) {
    String reserved = line.get(1, "reserved");
    if (!"0".equals(reserved)) {
      throw new com.yuvalshavit.effesvm.runtime.EffesLoadException("second token in a TYPE line must be 0");
    }
    String name = line.get(2, "typename");
    List<String> args = Arrays.asList(line.tailTokens(3));
    return new EffesType(name, args);
  }

  private static class Line {
    private final String[] tokens; // null if the line was actually empty, 0-element if a comment

    Line(String line) {
      line = line.trim();
      if (line.isEmpty()) {
        tokens = null;
      } else {
        List<String> tokensList = new ArrayList<>();
        SimpleTokenizer.tokenize(line).forEachRemaining(tokensList::add);
        this.tokens = tokensList.toArray(new String[tokensList.size()]);
      }
    }

    /** the line was literally empty, other than whitespace */
    public boolean isEmpty() {
      return tokens == null;
    }

    /** the line was either empty, or entirely a comment */
    boolean isEmptyOrComment() {
      return tokens == null || tokens.length == 0;
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

    String[] tailTokens(int from) {
      return Arrays.copyOfRange(tokens, from, tokens.length);
    }

    @Override
    public String toString() {
      return Arrays.toString(tokens);
    }
  }
}
