package com.yuvalshavit.effesvm.runtime.coverage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.runtime.DebugServer;
import com.yuvalshavit.effesvm.runtime.DebugServerContext;
import com.yuvalshavit.effesvm.runtime.EffesState;
import com.yuvalshavit.effesvm.runtime.ProgramCounter;
import com.yuvalshavit.effesvm.util.Average;

public class CodeCoverageDebugServer implements DebugServer {

  public static final String HASH_ALGORITHM = "SHA-1";
  public static final String REPORT_SUFFIX = ".txt";
  public static final String CUMULATIVE_DATA_SUFFIX = ".data";
  public static final String OVERALL_LABEL = "<total>";

  private final String outFileBase;
  private final PreviousFunctionDatas previous;
  private final Map<EffesFunctionId, FunctionData> functions;

  public CodeCoverageDebugServer(DebugServerContext context, String outFileBase) {
    this.outFileBase = outFileBase;
    previous = PreviousFunctionDatas.read(outFileBase + CUMULATIVE_DATA_SUFFIX);
    MessageDigest messageDigest;
    try {
      messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    functions = context.modules().values().stream().flatMap(m -> m.functions().stream()).collect(Collectors.toMap(
      EffesFunction::id,
      f -> createFunctionData(f, previous, messageDigest)
    ));
  }

  private static FunctionData createFunctionData(EffesFunction f, PreviousFunctionDatas previous, MessageDigest digest) {
    digest.reset();
    for (int i = 0; i < f.nOps(); ++i) {
      digest.update(f.opAt(i).info().toString().getBytes(StandardCharsets.UTF_8));
    }
    String hash = digest.getAlgorithm() + '$' + Base64.getEncoder().encodeToString(digest.digest());

    PreviousFunctionData previousData = previous.get(f.id());
    boolean[] seenOps = previousData != null && previousData.seenOps.length == f.nOps() && previousData.hash.equals(hash)
      ? previousData.seenOps
      : new boolean[f.nOps()];
    return new FunctionData(hash, seenOps, f);
  }

  @Override
  public void beforeAction(EffesState state) {
    ProgramCounter pc = state.pc();
    boolean[] ops = functions.get(pc.getCurrentFunction().id()).seenOps;
    ops[pc.getOpIdx()] = true;
  }

  @Override
  public void close() throws IOException {
    writeCumulativeData();
    writeReport();
  }

  private void writeCumulativeData() {
    previous.update(functions);
    previous.write();
  }


  private void writeReport() throws IOException {
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

    File outFile = new File(outFileBase + REPORT_SUFFIX);
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

  private static class FunctionData extends PreviousFunctionData {
    public final EffesFunction function;

    public FunctionData(String hash, boolean[] seenOps, EffesFunction function) {
      super(hash, seenOps);
      this.function = function;
    }
  }
}
