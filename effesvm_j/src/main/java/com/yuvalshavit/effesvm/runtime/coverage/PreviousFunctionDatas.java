package com.yuvalshavit.effesvm.runtime.coverage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.yuvalshavit.effesvm.load.EffesFunctionId;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class PreviousFunctionDatas {
  private static final char SEEN = '+';
  private static final char NOT_SEEN = '0';

  private final File file;
  private final NavigableMap<EffesFunctionId, PreviousFunctionData> functions;

  public static PreviousFunctionDatas read(String file) {
    NavigableMap<EffesFunctionId,PreviousFunctionData> previous = new TreeMap<>();
    File outFileCumulative = new File(file);
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
    return new PreviousFunctionDatas(outFileCumulative, previous);
  }

  public PreviousFunctionData get(EffesFunctionId id) {
    return functions.get(id);
  }

  public void update(Map<EffesFunctionId, ? extends PreviousFunctionData> functions) {
    this.functions.putAll(functions);
  }

  public void write() {
    try (FileWriter fw = new FileWriter(file);
         PrintWriter printer = new PrintWriter(fw))
    {
      functions.forEach((fid, data) -> {
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
}
