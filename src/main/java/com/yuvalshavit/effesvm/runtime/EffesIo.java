package com.yuvalshavit.effesvm.runtime;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public interface EffesIo {

  static EffesIo stdio() {
    return Stdio.instance;
  }

  EffesInput in();
  EffesOutput out();
  EffesOutput err();
  InputStream readFile(String name);
  OutputStream writeFile(String name);

  class Stdio implements EffesIo {
    private static final Stdio instance = new Stdio();
    private Stdio() {}

    private static final EffesInput stdinInput = new EffesInput.FromReader(new BufferedReader(new InputStreamReader(System.in)), "stdin");
    private static final EffesOutput stdoutOutput = new EffesOutput.FromWriter(System.out, "stdout");
    private static final EffesOutput stderrOutput = new EffesOutput.FromWriter(System.err, "stderr");

    @Override
    public EffesInput in() {
      return stdinInput;
    }

    @Override
    public EffesOutput out() {
      return stdoutOutput;
    }

    @Override
    public EffesOutput err() {
      return stderrOutput;
    }

    @Override
    public InputStream readFile(String name) {
      try {
        return new FileInputStream(name);
      } catch (FileNotFoundException e) {
        throw new EffesRuntimeException("while opening " + name + " for reading", e);
      }
    }

    @Override
    public OutputStream writeFile(String name) {
      try {
        return new FileOutputStream(name);
      } catch (FileNotFoundException e) {
        throw new EffesRuntimeException("while opening " + name + " for writing", e);
      }
    }
  }
}
