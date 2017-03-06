package com.yuvalshavit.effesvm.runtime;

import java.util.List;

public interface DebugServerMXBean {
  boolean isSuspended();
  void setSuspended(boolean isSuspended);

  List<String> getBreakpoints();
  void registerBreakpoint(String module, int line);
  boolean unregisterBreakpoint(String module, int line);

  List<String> stackTrace();
}
