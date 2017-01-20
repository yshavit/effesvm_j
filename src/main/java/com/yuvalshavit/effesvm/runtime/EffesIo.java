package com.yuvalshavit.effesvm.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class EffesIo {
  private static final BufferedReader stdinReader = new BufferedReader(new InputStreamReader(System.in));

  public static String readLine() {
    try {
      return stdinReader.readLine();
    } catch (IOException e) {
      throw new EffesIoException("while reading from stdin", e);
    }
  }

  public static void write(String string) {
    System.out.print(string);
  }
}
