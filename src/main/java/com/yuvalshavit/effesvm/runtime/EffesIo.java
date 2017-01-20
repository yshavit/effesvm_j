package com.yuvalshavit.effesvm.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public interface EffesIo {

  static EffesIo stdio() {
    return Stdio.instance;
  }

  String readLine();
  void out(String string);
  void err(String string);

  default void errLine(String string) {
    err(string);
    err("\n");
  }

  class Stdio implements EffesIo {
    private static final Stdio instance = new Stdio();
    private Stdio() {}

    private static final BufferedReader stdinReader = new BufferedReader(new InputStreamReader(System.in));

    @Override
    public String readLine() {
      try {
        return stdinReader.readLine();
      } catch (IOException e) {
        throw new EffesIoException("while reading from stdin", e);
      }
    }

    @Override
    public void out(String string) {
      System.out.print(string);
    }

    @Override
    public void err(String string) {
      System.err.print(string);
    }
  }
}
