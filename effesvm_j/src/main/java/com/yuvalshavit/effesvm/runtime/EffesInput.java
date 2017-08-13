package com.yuvalshavit.effesvm.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public interface EffesInput {
  String readLine();

  class FromReader implements EffesInput {
    private final String description;
    private BufferedReader reader;
    private final AtomicInteger linesRead = new AtomicInteger(0);

    public FromReader(BufferedReader reader, String description) {
      this.reader = reader;
      this.description = description;
    }

    public String readLine() {
      if (reader == null) {
        return null;
      }
      try {
        String result = reader.readLine();
        if (result == null) {
          reader.close();
          reader = null;
        } else {
          linesRead.incrementAndGet();
        }
        return result;
      } catch (IOException e) {
        throw new EffesRuntimeException("while reading line from " + description, e);
      }
    }

    @Override
    public String toString() {
      int nLines = linesRead.get();
      return String.format("%s(%d line%s read)", description, nLines, nLines == 1 ? "" : "s");
    }
  }
}
