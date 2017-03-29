package com.yuvalshavit.effesvm.runtime;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;

public interface EffesIo {

  static EffesIo stdio() {
    return Stdio.instance;
  }

  EffesInput in();
  void out(String string);
  void err(String string);
  InputStream readFile(String name);

  default void errLine(String string) {
    err(string);
    err("\n");
  }

  class Stdio implements EffesIo {
    private static final Stdio instance = new Stdio();
    private Stdio() {}

    private static final EffesInput stdinInput = new EffesInput.FromReader(
      new BufferedReader(new InputStreamReader(System.in)),
      "stdin");

    @Override
    public EffesInput in() {
      return stdinInput;
    }

    @Override
    public void out(String string) {
      System.out.print(string);
    }

    @Override
    public void err(String string) {
      System.err.print(string);
    }

    @Override
    public InputStream readFile(String name) {
      try {
        return new FileInputStream(name);
      } catch (FileNotFoundException e) {
        throw new EffesRuntimeException("while opening " + name + " for reading", e);
      }
    }
  }
}
