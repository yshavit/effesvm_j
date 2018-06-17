package com.yuvalshavit.effesvm.runtime;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.util.Average;

import lombok.AllArgsConstructor;

public class CodeCoverageDebugServer implements DebugServer {

  private final Map<EffesFunctionId, FunctionData> functions;
  public static final String OVERALL_LABEL = "<total>";
  private final String outFileName;

  public CodeCoverageDebugServer(DebugServerContext context, String outFileName) {
    this.outFileName = outFileName;
    functions = context.modules().values().stream().flatMap(m -> m.functions().stream()).collect(Collectors.toMap(
      EffesFunction::id,
      f -> new FunctionData(f, new boolean[f.nOps()])
    ));
  }

  @Override
  public void beforeAction(EffesState state) {
    ProgramCounter pc = state.pc();
    boolean[] ops = functions.get(pc.getCurrentFunction().id()).seenOps;
    ops[pc.getOpIdx()] = true;
  }

  @Override
  public void close() throws IOException {
    Average overall = new Average();
    NavigableMap<EffesFunctionId,Average> functionsAverages = new TreeMap<>();
    NavigableMap<EffesModule.Id,Average> modulesAverages = new TreeMap<>();
    functions.forEach((functionId, functionData) -> {
      boolean[] ops = functionData.seenOps;
      int nSeen = countSeen(ops);
      int nOps = ops.length;
      functionsAverages.put(functionId, new Average().add(nSeen, nOps));
      EffesModule.Id moduleId = functionId.getScope().getModuleId();
      modulesAverages.computeIfAbsent(moduleId, x -> new Average()).add(nSeen, nOps);
      overall.add(nSeen, nOps);
    });

    int maxModuleIdLen = modulesAverages.keySet().stream().mapToInt(m -> m.toString().length()).max().orElse(0);
    maxModuleIdLen = Math.max(maxModuleIdLen, OVERALL_LABEL.length());
    String moduleAvgFormat = "%-" + maxModuleIdLen + "s : %.1f%% (%d / %d)%n";

    File outFile = new File(outFileName);
    try (FileWriter fw = new FileWriter(outFile);
         PrintWriter printer = new PrintWriter(fw))
    {
      System.err.println("Writing code coverage report to " + outFile.getAbsoluteFile());
      printer.format(moduleAvgFormat, OVERALL_LABEL, overall.get() * 100, overall.total(), overall.count());
      modulesAverages.forEach((moduleId, avg) -> printer.format(moduleAvgFormat, moduleId, avg.get() * 100.0, avg.total(), avg.count()));
      printer.println();
      functionsAverages.forEach((functionId, avg) -> {
        String functionHeader = String.format("%s: %.1f%%:", functionId, avg.get() * 100.0);
        printer.println(functionHeader);
        //noinspection ReplaceAllDot
        printer.println(functionHeader.replaceAll(".", "-"));
        FunctionData functionData = functions.get(functionId);
        for (int i = 0; i < functionData.function.nOps(); ++i) {
          char seenMarker = functionData.seenOps[i] ? '+' : ' ';
          printer.append(seenMarker).append(' ').println(functionData.function.opAt(i).info().toString());
        }
        printer.println();
      });
    }
  }

  private int countSeen(boolean[] ops) {
    int count = 0;
    for (boolean op : ops) {
      if (op) {
        ++count;
      }
    }
    return count;
  }

  @AllArgsConstructor
  private class FunctionData {
    public final EffesFunction function;
    public boolean[] seenOps;
  }
}
