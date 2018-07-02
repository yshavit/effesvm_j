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
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.swing.DefaultListCellRenderer;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.ops.OpInfo;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgGetModules;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgSetBreakpoints;

import lombok.Data;

public class SourceDebugPane extends AbstractDebugLinePane<String> {
  private final Map<ModuleLine,MsgSetBreakpoints.Breakpoint> breakpointsPerLine;
  private final Map<EffesModule.Id,List<String>> moduleLines;

  protected SourceDebugPane(Map<EffesFunctionId, MsgGetModules.FunctionInfo> opsByFunction, Supplier<EffesFunctionId> activeFunction) {
    super(opsByFunction, activeFunction);
    String sourcePath = System.getenv("EFFES_SOURCEPATH");

    if (sourcePath == null) {
      moduleLines = Collections.emptyMap();
      breakpointsPerLine = Collections.emptyMap();
    } else {
      File sourceDir = new File(sourcePath);
      File[] files = sourceDir.listFiles(file -> file.isFile() && file.getName().endsWith(".ef"));
      if (files == null) {
        System.err.printf("not a directory: %s%n", sourceDir.getAbsolutePath());
        moduleLines = Collections.emptyMap();
        breakpointsPerLine = Collections.emptyMap();
      } else {
        moduleLines = new HashMap<>(files.length);
        for (File file : files) {
          try {
            List<String> lines = Files.lines(file.toPath()).collect(Collectors.toCollection(ArrayList::new));
            String format = "%" + (Integer.toString(lines.size()).length()) + "d %s";
            for (int i = 0; i < lines.size(); ++i) {
              lines.set(i, String.format(format, i, lines.get(i)));
            }
            String moduleName = file.getName().replaceAll("\\.ef$", "");
            moduleLines.put(new EffesModule.Id(moduleName), lines);
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
        breakpointsPerLine = new HashMap<>(opsByFunction.size());
        opsByFunction.forEach((functionId, functionInfo) -> {
          // TODO!
        });
      }
    }
    setCellRenderer(new DefaultListCellRenderer());
  }

  @Override
  protected MsgSetBreakpoints.Breakpoint getBreakpoint(EffesFunctionId visibleFunction, int clickedItemInList) {
    return breakpointsPerLine.get(new ModuleLine(visibleFunction, clickedItemInList));
  }

  @Override
  protected void showFunction(EffesFunctionId functionId, Consumer<String> addToModel) {
    List<String> lines = moduleLines.get(functionId.getScope().getModuleId());
    if (lines == null) {
      System.err.printf("couldn't find module source for %s%n", functionId);
    } else {
      lines.forEach(addToModel);
    }
  }

  @Override
  protected int getLineForOp(EffesFunctionId functionId, int opIdxWithinFunction) {
    MsgGetModules.FunctionInfo info = getInfoFor(functionId);
    if (info == null) {
      return -1;
    }

    OpInfo opInfo = info.ops().get(opIdxWithinFunction);
    return opInfo.sourceLineNumber();
  }

  @Data
  private static class ModuleLine {
    private final EffesFunctionId functionId;
    private final int line;
  }
}
