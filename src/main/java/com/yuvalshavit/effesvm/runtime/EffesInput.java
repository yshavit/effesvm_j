package com.yuvalshavit.effesvm.runtime;

import java.io.BufferedReader;
import java.io.IOException;

public interface EffesInput {
  String readLine();

  class FromReader implements EffesInput {
    private final String description;
    private BufferedReader reader;
    private int linesRead;

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
          ++linesRead;
        }
        return result;
      } catch (IOException e) {
        throw new EffesRuntimeException("while reading line from " + description, e);
      }
    }

    @Override
    public String toString() {
      return String.format("%s(%d line%s read)", description, linesRead, linesRead == 1 ? "" : "s");
    }
  }
}
