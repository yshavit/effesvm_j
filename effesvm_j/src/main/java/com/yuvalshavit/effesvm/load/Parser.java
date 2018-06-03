package com.yuvalshavit.effesvm.load;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.yuvalshavit.effesvm.runtime.EffesType;

public class Parser {
  public static final String EFCT_0_HEADER = "efct 0";

  private Parser() {}

  public static OutlinedModule parse(EffesModule.Id module, List<String> lines) {
    if (lines.isEmpty()) {
      new OutlinedModule(Collections.emptyMap(), Collections.emptyMap());
    }
    if (!lines.get(0).equals(EFCT_0_HEADER)) {
      throw new IllegalArgumentException("file must start with \"" + EFCT_0_HEADER + "\"");
    }

    List<EfctLine> efctLines = new ArrayList<>(lines.size());
    for (int i = 1; i < lines.size(); i++) { // Note! Starting from index 1, since index 0 is the header
      efctLines.add(new EfctLine(lines.get(i), i));
    }
    Iterator<EfctLine> tokenizedLines = efctLines.iterator();

    Map<EffesFunctionId,OutlinedModule.FunctionParse> functions = new HashMap<>();
    Map<String,EffesType> types = new HashMap<>();
    int lineCount = 0;
    while (tokenizedLines.hasNext()) {
      ++lineCount;
      EfctLine line = tokenizedLines.next();
      if (line.isEmptyOrComment()) {
        continue;
      }
      try {
        String declarationType = line.get(0, "declaration type");
        switch (declarationType) {
          case "FUNC":
            String className = line.get(1, "classname");
            String functionName = line.get(2, "functionname");
            OutlinedModule.FunctionParse parsedFunction = parseFunction(
              tokenizedLines,
              line.get(3, "nArgs", Integer::parseInt),
              line.get(4, "nRv", Integer::parseInt),
              line.get(5, "nGenerics", Integer::parseInt));
            if (line.nTokens() != 6) {
              throw new IllegalArgumentException("too many arguments for FUNC declaration");
            }
            functions.put(EffesFunctionId.parse(className, functionName, module), parsedFunction);
            break;
          case "TYPE":
            EffesType type = parseType(line, module);
            types.put(type.name(), type);
            break;
          default:
            throw new IllegalArgumentException("unrecognized declaration type: " + declarationType);
        }
      } catch (Exception e) {
        String originalMessage = e.getMessage();
        String annotation = "at line " + (lineCount + 1) + " of " + module;
        String message = originalMessage == null
          ? annotation
          : (originalMessage + " " + annotation);
        throw new EffesLoadException(message, e);
      }
    }
    return new OutlinedModule(types, functions);
  }

  private static OutlinedModule.FunctionParse parseFunction(
    Iterator<EfctLine> lines,
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

    List<EfctLine> opsLines = new ArrayList<>();
    while (lines.hasNext()) {
      EfctLine line = lines.next();
      if (line.isEmpty()) {
        break;
      } else if (line.isEmptyOrComment()) {
        continue;
      }
      opsLines.add(line);
    }
    return new OutlinedModule.FunctionParse(nArgs, hasRv, opsLines);
  }

  private static EffesType parseType(EfctLine line, EffesModule.Id module) {
    String reserved = line.get(1, "reserved");
    if (!"0".equals(reserved)) {
      throw new EffesLoadException("second token in a TYPE line must be 0");
    }
    String name = line.get(2, "typename");
    List<String> args = Arrays.asList(line.tailTokens(3));
    return new EffesType(module, name, args);
  }

}
