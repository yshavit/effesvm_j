package com.yuvalshavit.effesvm.load;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.yuvalshavit.effesvm.ops.OperationFactories;
import com.yuvalshavit.effesvm.ops.UnlinkedOperation;
import com.yuvalshavit.effesvm.runtime.EffesType;
import com.yuvalshavit.effesvm.util.SequencedIterator;
import com.yuvalshavit.effesvm.util.SimpleTokenizer;

public class Parser {
  public static final String EFCT_0_HEADER = "efct 0";
  private final Function<String,OperationFactories.ReflectiveOperationBuilder> opsFactories;

  public Parser(Function<String,OperationFactories.ReflectiveOperationBuilder> opsFactories) {
    this.opsFactories = opsFactories;
  }

  public EffesModule<UnlinkedOperation> parse(EffesModule.Id module, SequencedIterator<String> lines) {
    if (!lines.hasNext()) {
      return new EffesModule<>(Collections.emptyList(), Collections.emptyList());
    }
    if (!lines.next().equals(EFCT_0_HEADER)) {
      throw new IllegalArgumentException("file must start with \"" + EFCT_0_HEADER + "\"");
    }

    SequencedIterator<Line> tokenizedLines = lines.mapView(Line::new);

    Map<EffesFunction.Id,EffesFunction<UnlinkedOperation>> functions = new HashMap<>();
    Map<String,EffesType> types = new HashMap<>();
    while (tokenizedLines.hasNext()) {
      Line line = tokenizedLines.next();
      if (line.isEmptyOrComment()) {
        continue;
      }
      try {
        String declarationType = line.get(0, "declaration type");
        switch (declarationType) {
          case "FUNC":
            String className = line.get(1, "classname");
            String functionName = line.get(2, "functionname");
            EffesFunction<UnlinkedOperation> parsedFunction = parseFunction(
              module,
              tokenizedLines,
              className,
              functionName,
              line.get(3, "nArgs", Integer::parseInt),
              line.get(4, "nRv", Integer::parseInt),
              line.get(5, "nGenerics", Integer::parseInt));
            assert line.tokens != null;
            if (line.tokens.length != 6) {
              throw new IllegalArgumentException("too many arguments for FUNC declaration");
            }
            functions.put(parsedFunction.id(), parsedFunction);
            break;
          case "TYPE":
            EffesType type = parseType(line);
            types.put(type.name(), type);
            break;
          default:
            throw new IllegalArgumentException("unrecognized declaration type: " + declarationType);
        }
      } catch (Exception e) {
        String originalMessage = e.getMessage();
        String annotation = "at line " + tokenizedLines.count() + " of " + module;
        String message = originalMessage == null
          ? annotation
          : (originalMessage + " " + annotation);
        throw new EffesLoadException(message, e);
      }
    }
    return new EffesModule<>(types.values(), functions.values());
  }

  private EffesFunction<UnlinkedOperation> parseFunction(
    EffesModule.Id module,
    SequencedIterator<Line> lines,
    String className,
    String functionName,
    int nArgs,
    int nRv,
    int nGenerics)
  {
    if (nGenerics != 0 || nArgs < 0) {
      throw new IllegalArgumentException("invalid FUNC declaration");
    }
    final boolean hasRv;
    if (nRv == 0) {
      hasRv = false;
    } else if (nRv == 1) {
      hasRv = true;
    } else {
      throw new IllegalArgumentException("invalid FUNC declaration (nRv = " + nRv + ")");
    }

    List<UnlinkedOperation> ops = new ArrayList<>();
    while (lines.hasNext()) {
      Line line = lines.next();
      if (line.isEmpty()) {
        break;
      } else if (line.isEmptyOrComment()) {
        continue;
      }
      String opcode = line.get(0, "opcode");
      OperationFactories.ReflectiveOperationBuilder opBuilder = opsFactories.apply(opcode);
      if (opBuilder == null) {
        throw new EffesLoadException("no such op: " + opcode);
      }
      UnlinkedOperation op = opBuilder.build(module, lines.count(), line.tailTokens(1));
      ops.add(op);
    }
    return new EffesFunction<>(new EffesFunction.Id(className, functionName), -1, hasRv, nArgs, ops);
  }

  private EffesType parseType(Line line) {
    String reserved = line.get(1, "reserved");
    if (!"0".equals(reserved)) {
      throw new EffesLoadException("second token in a TYPE line must be 0");
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
