package com.yuvalshavit.effesvm.runtime.debugger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.yuvalshavit.effesvm.load.EfctScopeDesc;
import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgSetBreakpoints;

public class DebuggerGuiState {
  private static final String BREAKPOINT_PREFIX = "BR";
  private static final Pattern BREAKPOINT_PATTERN = Pattern.compile(BREAKPOINT_PREFIX + ": (\\S+) (\\S+) (\\S+)");
  private final File saveFile;
  private final Set<MsgSetBreakpoints.Breakpoint> breakpoints;

  public DebuggerGuiState(File saveFile) {
    this.saveFile = saveFile;
    this.breakpoints = Collections.synchronizedSet(new HashSet<>());
    if (saveFile.exists()) {
      load();
    }
  }

  public Set<MsgSetBreakpoints.Breakpoint> getBreakpoints() {
    synchronized (breakpoints) {
      return Collections.unmodifiableSet(new HashSet<>(breakpoints));
    }
  }

  public void setBreakpoint(MsgSetBreakpoints.Breakpoint breakpoint, boolean on) {
    boolean changed = on
      ? breakpoints.add(breakpoint)
      : breakpoints.remove(breakpoint);
    if (changed) {
      save();
    }
  }

  private void load() {
    try {
      Files.lines(saveFile.toPath()).forEach(line -> {
        Matcher breakpointMatcher = BREAKPOINT_PATTERN.matcher(line);
        if (breakpointMatcher.matches()) {
          int opIdx;
          try {
            opIdx = Integer.parseInt(breakpointMatcher.group(3));
          } catch (NumberFormatException e) {
            System.err.println("invalid breakpoint line: " + line);
            return;
          }
          String scopeId = breakpointMatcher.group(1);
          String functionName = breakpointMatcher.group(2);
          EfctScopeDesc scope = EfctScopeDesc.parse(scopeId, null);
          EffesFunctionId functionId = new EffesFunctionId(scope, functionName);
          breakpoints.add(new MsgSetBreakpoints.Breakpoint(functionId, opIdx));
        } else {
          System.err.println("invalid line: " + line);
        }
      });
    } catch (IOException e) {
      System.err.printf("Couldn't load %s: %s%n", saveFile.getAbsolutePath(), e);
    }
  }

  private void save() {
    Iterable<String> breakpointIter = () -> this.breakpoints
      .stream()
      .map(b -> String.format("%s: %s %s %s", BREAKPOINT_PREFIX, b.getFid().getScope(), b.getFid().getFunctionName(), b.getOpIdx()))
      .iterator();
    try {
      Files.write(saveFile.toPath(), breakpointIter);
    } catch (IOException e) {
      System.err.printf("Couldn't save %s: %s%n", saveFile.getAbsolutePath(), e);
    }
  }
}
