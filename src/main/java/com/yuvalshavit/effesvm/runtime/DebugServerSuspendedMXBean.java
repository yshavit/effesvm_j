package com.yuvalshavit.effesvm.runtime;

import java.util.List;

public interface DebugServerSuspendedMXBean {
  void resume();
  void step();
  List<String> getStackTrace();
  List<String> getDebugState();
}
