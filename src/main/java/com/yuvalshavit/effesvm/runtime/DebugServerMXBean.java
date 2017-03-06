package com.yuvalshavit.effesvm.runtime;

import java.util.List;

@SuppressWarnings("unused") // reflection via mxbean
public interface DebugServerMXBean {
  boolean isSuspended();
  void suspend();

  List<String> getBreakpoints();
  void registerBreakpoint(String module, int line);
  boolean unregisterBreakpoint(String module, int line);
}
