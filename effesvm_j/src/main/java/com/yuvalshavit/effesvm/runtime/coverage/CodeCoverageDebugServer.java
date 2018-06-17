package com.yuvalshavit.effesvm.runtime.coverage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.runtime.DebugServer;
import com.yuvalshavit.effesvm.runtime.DebugServerContext;
import com.yuvalshavit.effesvm.runtime.EffesState;
import com.yuvalshavit.effesvm.runtime.ProgramCounter;
import com.yuvalshavit.effesvm.util.Average;

import lombok.AllArgsConstructor;

public class CodeCoverageDebugServer implements DebugServer {

  public static final String HASH_ALGORITHM = "SHA-1";
  public static final char SEEN = '+';
  public static final char NOT_SEEN = '0';
  public static final String REPORT_SUFFIX = ".txt";
  public static final String CUMULATIVE_DATA_SUFFIX = ".data";
  public static final String OVERALL_LABEL = "<total>";

  private final String outFileBase;
  private final Map<EffesFunctionId, PreviousFunctionData> previous;
  private final Map<EffesFunctionId, FunctionData> functions;

  public CodeCoverageDebugServer(DebugServerContext context, String outFileBase) {
    this.outFileBase = outFileBase;
    previous = readPrevious(outFileBase);
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

  private static Map<EffesFunctionId, PreviousFunctionData> readPrevious(String outFileBase) {
    Map<EffesFunctionId,PreviousFunctionData> previous = new HashMap<>();
    File outFileCumulative = new File(outFileBase + CUMULATIVE_DATA_SUFFIX);
    if (outFileCumulative.exists()) {
      Path path = outFileCumulative.toPath();
      try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
        lines.forEach(line -> {
          String[] split = line.split("\\s+", 3);
          String functionIdString = split[0];
          String hashString = split[1];
          String oldSeenStr = split[2];
          EffesFunctionId functionId = EffesFunctionId.tryParse(functionIdString);
          if (functionId != null) {
            boolean[] oldOpsSeen = new boolean[oldSeenStr.length()];
            for (int i = 0; i < oldSeenStr.length(); ++i) {
              oldOpsSeen[i] = oldSeenStr.charAt(i) == SEEN;
            }
            PreviousFunctionData functionData = new PreviousFunctionData(hashString, oldOpsSeen);
            previous.put(functionId, functionData);
          }
        });
      } catch (IOException e) {
        System.err.printf("Couldn't read previous code coverage from %s: %s", outFileCumulative, e.getMessage());
      }
    }
    return previous;
  }

  private void writeFunctionData() {
    NavigableMap<EffesFunctionId,PreviousFunctionData> cumulative = new TreeMap<>(previous);
    functions.forEach((fid, update) -> cumulative.put(fid, new PreviousFunctionData(update.hash, update.seenOps)));

    try (FileWriter fw = new FileWriter(outFileBase + CUMULATIVE_DATA_SUFFIX);
         PrintWriter printer = new PrintWriter(fw))
    {
      cumulative.forEach((fid, data) -> {
        printer.printf("%s %s ", fid, data.hash);
        for (boolean seen : data.seenOps) {
          printer.print(seen ? SEEN : NOT_SEEN);
        }
        printer.println();
      });
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static FunctionData createFunctionData(EffesFunction f, Map<EffesFunctionId, PreviousFunctionData> previous, MessageDigest digest) {
    digest.reset();
    for (int i = 0; i < f.nOps(); ++i) {
      digest.update(f.opAt(i).info().toString().getBytes(StandardCharsets.UTF_8));
    }
    String hash = digest.getAlgorithm() + '$' + Base64.getEncoder().encodeToString(digest.digest());

    PreviousFunctionData previousData = previous.get(f.id());
    boolean[] seenOps = previousData != null && previousData.seenOps.length == f.nOps() && previousData.hash.equals(hash)
      ? previousData.seenOps
      : new boolean[f.nOps()];
    return new FunctionData(f, hash, seenOps);
  }

  @Override
  public void beforeAction(EffesState state) {
    ProgramCounter pc = state.pc();
    boolean[] ops = functions.get(pc.getCurrentFunction().id()).seenOps;
    ops[pc.getOpIdx()] = true;
  }

  @Override
  public void close() throws IOException {
    writeFunctionData();
    writeReport();
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
          char seenMarker = functionData.seenOps[i] ? SEEN : ' ';
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
  private static class PreviousFunctionData {
    public final String hash;
    public final boolean[] seenOps;
  }

  @AllArgsConstructor
  private static class FunctionData {
    public final EffesFunction function;
    public final String hash;
    public final boolean[] seenOps;
  }
}
