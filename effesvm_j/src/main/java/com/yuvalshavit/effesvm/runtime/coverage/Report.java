package com.yuvalshavit.effesvm.runtime.coverage;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.IntStream;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.util.Average;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
class Report {
  private static final String OVERALL_LABEL = "<total>";
  private static final String SUMMARY_FUNCTION_BREAKDOWN_PREFIX = "    ";

  private final Average overall;
  private final NavigableMap<EffesFunctionId, Average> functionsAverages;
  private final NavigableMap<EffesModule.Id, Average> modulesAverages;
  private final Function<EffesFunctionId, FunctionData> functions;

  void print(PrintWriter printer) {
    printSummary(printer);
    printer.println();
    printFunctionOps(printer);
  }

  private void printSummary(PrintWriter printer) {
    BiConsumer<String, Average> formatter = summaryFormatter(printer);

    Map<EffesModule.Id, NavigableSet<PrioritizedComparable<EffesFunctionId>>> functionsByModule = new HashMap<>();
    functionsAverages.keySet().forEach(fid -> {
      Average functionAvg = functionsAverages.get(fid);
      int priority = functionAvg.total() - functionAvg.count(); // the more unseen, the higher the priority
      functionsByModule.computeIfAbsent(fid.getScope().getModuleId(), x -> new TreeSet<>()).add(new PrioritizedComparable<>(fid, priority));
    });

    formatter.accept(OVERALL_LABEL, overall);
    printer.println();
    modulesAverages.entrySet().stream().map(entry -> new PrioritizedComparable<>(entry.getKey(), entry.getValue().total() - entry.getValue().count()))
      .sorted()
      .map(pc -> pc.comparable)
      .forEach(moduleId -> {
        Average moduleAvg = modulesAverages.get(moduleId);
        formatter.accept(moduleId.toString(), moduleAvg);
        functionsByModule.get(moduleId).forEach(fid -> {
          Average functionAvg = functionsAverages.get(fid.comparable);
          formatter.accept(SUMMARY_FUNCTION_BREAKDOWN_PREFIX + fid.comparable.getFunctionName(), functionAvg);
        });
        printer.println();
      });
  }

  private BiConsumer<String, Average> summaryFormatter(PrintWriter printer) {
    int longestModule = modulesAverages.keySet().stream().mapToInt(m -> m.toString().length()).max().orElse(0);
    int longestFunction = functionsAverages.keySet().stream().map(EffesFunctionId::getFunctionName).mapToInt(String::length).max().orElse(0)
      + SUMMARY_FUNCTION_BREAKDOWN_PREFIX.length();
    int firstColumnLen = IntStream.of(longestModule, longestFunction).max().orElse(OVERALL_LABEL.length());
    String formatStr = "%-" + firstColumnLen + "s : %5.1f%% (%d / %d)%n";
    return (label, avg) -> printer.format(formatStr, label, avg.get() * 100.0, avg.total(), avg.count());
  }

  private void printFunctionOps(PrintWriter printer) {
    functionsAverages.forEach((EffesFunctionId functionId, Average avg) -> {
      String functionHeader = String.format("%s: %.1f%%:", functionId, avg.get() * 100.0);
      printer.println(functionHeader);
      //noinspection ReplaceAllDot
      printer.println(functionHeader.replaceAll(".", "-"));
      FunctionData functionData = functions.apply(functionId);
      for (int i = 0; i < functionData.function.nOps(); ++i) {
        char seenMarker = functionData.seenOps[i]
          ? '+'
          : ' ';
        printer.append(seenMarker).append(' ').println(functionData.function.opAt(i).info().toString());
      }
      printer.println();
    });
  }

  @Data
  private static class PrioritizedComparable<T extends Comparable<T>> implements Comparable<PrioritizedComparable<T>> {
    private final T comparable;
    private final int priority;

    @Override
    public int compareTo(PrioritizedComparable<T> o) {
      int cmp = Integer.compare(priority, o.priority); // lower number is higher priority
      if (cmp == 0) {
        cmp = comparable.compareTo(o.comparable);
      }
      return cmp;
    }
  }
}
