package com.yuvalshavit.effesvm.runtime.debugger;

import java.util.List;

@SuppressWarnings("unused") // reflection via mxbean
public interface DebugServerMXBean {
  boolean isSuspended();
  void suspend();
  void resume();
  void step();

  List<String> getStackTrace();
  List<String> getDebugState();

  List<String> getBreakpoints();
  void registerBreakpoint(String module, int line);
  boolean unregisterBreakpoint(String module, int line);
}
