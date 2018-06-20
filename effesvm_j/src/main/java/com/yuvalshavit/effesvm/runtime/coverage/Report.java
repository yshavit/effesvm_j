package com.yuvalshavit.effesvm.runtime.coverage;

import java.io.PrintWriter;
import java.util.NavigableMap;
import java.util.function.Function;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.util.Average;

import lombok.AllArgsConstructor;

@AllArgsConstructor
class Report {
  private static final String OVERALL_LABEL = "<total>";

  private final Average overall;
  private final NavigableMap<EffesFunctionId,Average> functionsAverages;
  private final NavigableMap<EffesModule.Id,Average> modulesAverages;
  private final Function<EffesFunctionId,FunctionData> functions;

  void print(PrintWriter printer) {
    int maxModuleIdLen = modulesAverages.keySet().stream().mapToInt(m -> m.toString().length()).max().orElse(0);
    maxModuleIdLen = Math.max(maxModuleIdLen, OVERALL_LABEL.length());
    String moduleAvgFormat = "%-" + maxModuleIdLen + "s : %.1f%% (%d / %d)%n";

    printer.format(moduleAvgFormat, OVERALL_LABEL, overall.get() * 100, overall.total(), overall.count());
    modulesAverages.forEach((moduleId, avg) -> printer.format(moduleAvgFormat, moduleId, avg.get() * 100.0, avg.total(), avg.count()));
    printer.println();
    functionsAverages.forEach((functionId, avg) -> {
      String functionHeader = String.format("%s: %.1f%%:", functionId, avg.get() * 100.0);
      printer.println(functionHeader);
      //noinspection ReplaceAllDot
      printer.println(functionHeader.replaceAll(".", "-"));
      FunctionData functionData = functions.apply(functionId);
      for (int i = 0; i < functionData.function.nOps(); ++i) {
        char seenMarker = functionData.seenOps[i] ? '+' : ' ';
        printer.append(seenMarker).append(' ').println(functionData.function.opAt(i).info().toString());
      }
      printer.println();
    });
  }

}
