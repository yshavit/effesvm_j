package com.yuvalshavit.effesvm.test;

import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.Yaml;

import com.google.common.collect.Iterables;
import com.yuvalshavit.effesvm.load.Parser;
import com.yuvalshavit.effesvm.runtime.EffesIo;
import com.yuvalshavit.effesvm.runtime.EvmRunner;

public class EndToEndTest {
  @DataProvider(name = "tests")
  public Iterator<Object[]> tests() throws IOException {
    String packageName = getClass().getPackage().getName();
    Reflections reflections = new Reflections(packageName, new ResourcesScanner());
    Set<String> yamlPaths = reflections.getResources(Pattern.compile(".*\\.yaml"));
    List<Object[]> tests = new ArrayList<>(yamlPaths.size() * 2);
    Yaml yamlParser = new Yaml();
    for (String path : yamlPaths) {
      path = path.substring(packageName.length() + 1); // since it's relative to the package. +1 for the /
      try (InputStream yaml = getClass().getResourceAsStream(path)) {
        Case testCase = yamlParser.loadAs(yaml, Case.class);
        if (testCase.runs == null || testCase.runs.isEmpty()) {
          throw new IllegalArgumentException("no runs in " + yamlPaths);
        }
        for (Run run : testCase.runs) {
          run.efct = testCase.efct;
          run.description = path;
          tests.add(new Object[] { run });
        }
      }
    }
    return tests.iterator();
  }

  @Test(dataProvider = "tests")
  public void run(Run run) {
    Iterator<String> efct = Iterables.concat(
      Collections.singleton(Parser.EFCT_0_HEADER),
      Arrays.asList(run.efct.split("\\n")))
      .iterator();

    InMemoryIo io = new InMemoryIo(run.in);
    int exitCode = EvmRunner.run(efct, io);
    assertEquals(exitCode, run.exit, "exit code");
    assertEquals(io.out.toString().trim(), run.out.trim(), "stdout");
    assertEquals(io.err.toString().trim(), run.err.trim(), "stderr");
  }

  public static class Case {
    public String efct;
    public List<Run> runs;
  }

  public static class Run {
    private String efct;
    private String description;
    public String in;
    public String out = "";
    public String err = "";
    public int exit = -1;

    @Override
    public String toString() {
      return description == null ? super.toString() : description;
    }
  }

  private static class InMemoryIo implements EffesIo {
    private final Iterator<String> in;
    private final StringBuilder out;
    private final StringBuilder err;

    private InMemoryIo(String in) {
      List<String> inLines = in == null ? Collections.emptyList() : Arrays.asList(in.split("\\n"));
      this.in = inLines.iterator();
      out = new StringBuilder();
      err = new StringBuilder();
    }

    @Override
    public String readLine() {
      return in.hasNext() ? in.next() : null;
    }

    @Override
    public void out(String string) {
      out.append(string);
    }

    @Override
    public void err(String string) {
      err.append(string);
    }
  }
}