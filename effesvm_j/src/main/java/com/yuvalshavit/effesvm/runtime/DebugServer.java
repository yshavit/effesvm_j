package com.yuvalshavit.effesvm.runtime;

import java.io.Closeable;
import java.io.IOException;

public interface DebugServer extends Closeable {
  void beforeAction(EffesState state);
  void close() throws IOException;

  DebugServer noop = new DebugServer() {
    @Override
    public void beforeAction(EffesState state) {}

    @Override
    public void close() throws IOException {}
  };
}
