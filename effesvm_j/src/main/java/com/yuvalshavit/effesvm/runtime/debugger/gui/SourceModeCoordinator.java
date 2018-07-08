package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class SourceModeCoordinator {

  private Mode mode = Mode.SOURCE;
  private final Set<Consumer<Mode>> listeners = new HashSet<>();

  public Mode getMode() {
    return mode;
  }

  public void setMode(Mode mode) {
    if (mode != this.mode) {
      this.mode = Objects.requireNonNull(mode, "mode can't be null");
      listeners.forEach(l -> l.accept(mode));
    }
  }

  void addSourceViewListener(Consumer<Mode> callback) {
    if (listeners.add(callback)) {
      callback.accept(mode);
    }
  }

  public enum Mode {
    EFCT,
    SOURCE,
    ;
  }

}
