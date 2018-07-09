package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.text.StringEscapeUtils;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.ops.OpInfo;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgGetModules;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgSetBreakpoints;

import lombok.Data;

public class SourceDebugPane extends AbstractDebugLinePane<SourceDebugPane.SourceLine> {
  private final Map<ModuleLine,MsgSetBreakpoints.Breakpoint> breakpointsPerLine;
  private final Map<EffesModule.Id,List<SourceLine>> moduleLines;
  private final Map<EffesFunctionId,Integer> firstLinePerFunction;

  protected SourceDebugPane(Map<EffesFunctionId, MsgGetModules.FunctionInfo> opsByFunction, Supplier<EffesFunctionId> visibleFunction) {
    super(opsByFunction, visibleFunction);
    String sourcePath = System.getenv("EFFES_SOURCEPATH");

    if (sourcePath == null) {
      moduleLines = Collections.emptyMap();
      breakpointsPerLine = Collections.emptyMap();
      firstLinePerFunction = Collections.emptyMap();
    } else {
      File sourceDir = new File(sourcePath);
      File[] files = sourceDir.listFiles(file -> file.isFile() && file.getName().endsWith(".ef"));
      if (files == null) {
        System.err.printf("not a directory: %s%n", sourceDir.getAbsolutePath());
        moduleLines = Collections.emptyMap();
        breakpointsPerLine = Collections.emptyMap();
        firstLinePerFunction = Collections.emptyMap();
      } else {
        moduleLines = new HashMap<>(files.length);
        for (File file : files) {
          try {
            List<String> lines = Files.lines(file.toPath()).collect(Collectors.toCollection(ArrayList::new));
            String format = SourceLine.lineFormatFactory.apply(lines.size());
            List<SourceLine> lineObjects = new ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); ++i) {
              SourceLine label = new SourceLine(format, i, lines.get(i));
              lineObjects.add(label);
            }
            String moduleName = file.getName().replaceAll("\\.ef$", "");
            moduleLines.put(new EffesModule.Id(moduleName), lineObjects);
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
        breakpointsPerLine = new HashMap<>(opsByFunction.size());
        opsByFunction.forEach((functionId, functionInfo) -> {
          // TODO!
        });
        firstLinePerFunction = new HashMap<>(opsByFunction.size());
        opsByFunction.forEach((functionId, info) -> info.ops().stream()
          .mapToInt(OpInfo::sourceLineNumber)
          .min()
          .ifPresent(line -> firstLinePerFunction.put(functionId, line)));
      }
    }
  }

  @Override
  protected MsgSetBreakpoints.Breakpoint getBreakpoint(EffesFunctionId visibleFunction, int clickedItemInList) {
    return breakpointsPerLine.get(new ModuleLine(visibleFunction, clickedItemInList));
  }

  @Override
  protected int showFunction(EffesFunctionId functionId, Consumer<SourceLine> addToModel) {
    List<SourceLine> lines = moduleLines.get(functionId.getScope().getModuleId());
    if (lines == null) {
      System.err.printf("couldn't find module source for %s%n", functionId);
    } else {
      lines.forEach(addToModel);
    }
    return firstLinePerFunction.getOrDefault(functionId, -1);
  }

  @Override
  protected int getLineForOp(EffesFunctionId functionId, int opIdxWithinFunction) {
    MsgGetModules.FunctionInfo info = getInfoFor(functionId);
    if (info == null) {
      return -1;
    }

    OpInfo opInfo = info.ops().get(opIdxWithinFunction);
    return opInfo.sourceLineNumber() - 1; // -1 because the debug symbols are written as 1-indexed
  }

  @Override
  protected IntStream getOpsForLine(EffesFunctionId functionId, int lineWithinModel) {
    return IntStream.empty(); // TODO!
  }

  @Override
  protected void preprocessLine(SourceLine line, boolean isCurrentlyRunning) {
    int highlight = isCurrentlyRunning
      ? getCurrentlyRunningOpInfo().sourcePositionInLine()
      : -1;
    line.setHighlight(highlight);
  }

  @Data
  private static class ModuleLine {
    private final EffesFunctionId functionId;
    private final int line;
  }

  static class SourceLine {
    private static final String SUFFIX = "</html>";
    private static final String HIGHLIGHT_TAG_START = "<font color=\"fuchsia\"><strong>";
    private static final String HIGHLIGHT_TAG_END = "</strong></font>";
    private static final String LINE_NUMBER_TAG = "i";
    private static final IntFunction<String> lineFormatFactory = totalLines ->
      "<html><" + LINE_NUMBER_TAG +">%" + (Integer.toString(totalLines + 1).length()) + "d</" + LINE_NUMBER_TAG + ">: ";
    private final String prefixAndLineNumber;
    private final String text;
    private int highlight = -1;

    public SourceLine(String lineNumberFormat, int lineNumber, String text) {
      this.prefixAndLineNumber = String.format(lineNumberFormat, lineNumber);
      this.text = text;
    }

    public void setHighlight(int highlight) {
      if (highlight >= text.length()) {
        int capped = text.length() - 1;
        System.err.printf("capping highlight from %d to %d: %s%n", highlight, capped, text);
        highlight = capped;
      }
      this.highlight = highlight;
    }

    @Override
    public String toString() {
      StringEscapeUtils.Builder escaper = StringEscapeUtils.builder(StringEscapeUtils.ESCAPE_HTML3);
      escaper.append(prefixAndLineNumber);
      if (highlight < 0) {
        escaper.escape(text);
      } else {
        // invariant in setHighlight ensures that highlight < text.length
        escaper
          .escape(text.substring(0, highlight - 1))
          .append(HIGHLIGHT_TAG_START)
          .escape(text.substring(highlight - 1, highlight))
          .append(HIGHLIGHT_TAG_END)
          .append(text.substring(highlight));
      }
      return escaper.append(SUFFIX).toString().replace(" ", "&nbsp;");
    }
  }
}
