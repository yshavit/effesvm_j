package com.yuvalshavit.effesvm.runtime;

import java.io.PrintStream;

public interface EffesOutput {
  void write(String text);

  class FromWriter implements EffesOutput {
    private final String description;
    private final PrintStream writer;
    private volatile int linesWritten;
    private volatile int charsThisLine;

    public FromWriter(PrintStream writer, String description) {
      this.writer = writer;
      this.description = description;
    }

    @Override
    public void write(String text) {
      try {
        writer.append(text);
        writer.flush();
        updateStats(text);
      } catch (RuntimeException e) {
        throw new RuntimeException("while writing to " + description, e);
      }
    }

    @Override
    public String toString() {
      int lines;
      int extraChars;
      synchronized (this) {
        lines = linesWritten;
        extraChars = charsThisLine;
      }
      return String.format("%s(%d line%s + %d char%s)", description, lines, lines == 1 ? "" : "s", extraChars, extraChars == 1 ? "" : "s");
    }

    private synchronized void updateStats(String text) {
      for (int i = 0, len = text.length(); i < len; ++i) {
        char c = text.charAt(i);
        if (c == '\n') {
          ++linesWritten;
          charsThisLine = 0;
        } else {
          ++charsThisLine;
        }
      }
    }
  }
}
