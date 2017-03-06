package com.yuvalshavit.effesvm.test;

import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.Yaml;

import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.load.Parser;
import com.yuvalshavit.effesvm.runtime.EffesIo;
import com.yuvalshavit.effesvm.runtime.EvmRunner;

public class EndToEndTest {
  private static final String DEFAULT_MODULE_NAME = "main";

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
        if (testCase.otherModules.containsKey(DEFAULT_MODULE_NAME)) {
          throw new RuntimeException("can't have an explicitly-named module called " + DEFAULT_MODULE_NAME);
        }
        Map<String,String> efcts = new HashMap<>(testCase.otherModules.size() + 1);
        efcts.putAll(testCase.otherModules);
        efcts.put(DEFAULT_MODULE_NAME, testCase.efct);
        for (Run run : testCase.runs) {
          run.efctByModule = efcts;
          run.description = (run.description == null)
            ? path
            : (path + ": " + run.description);
          tests.add(new Object[] { run });
        }
      }
    }
    return tests.iterator();
  }

  @Test(dataProvider = "tests")
  public void run(Run run) {
    Map<EffesModule.Id,Iterable<String>> modules = new HashMap<>(run.efctByModule.size());
    for (Map.Entry<String,String> efctByModule : run.efctByModule.entrySet()) {
      EffesModule.Id module = EffesModule.Id.of(efctByModule.getKey());
      Iterable<String> efct = () -> Stream.concat(
        Stream.of(Parser.EFCT_0_HEADER),
        Stream.of(efctByModule.getValue().split("\\n")))
        .iterator();
      modules.put(module, efct);
    }

    InMemoryIo io = new InMemoryIo(run.in);
    int exitCode = EvmRunner.run(modules, EffesModule.Id.of(DEFAULT_MODULE_NAME), io, run.stackSize, null);
    assertEquals(exitCode, run.exit, "exit code");
    assertEquals(io.out.toString().trim(), run.out.trim(), "stdout");
    assertEquals(io.err.toString().trim(), run.err.trim(), "stderr");
  }

  public static class Case {
    public String efct;
    public List<Run> runs;
    public Map<String,String> otherModules = Collections.emptyMap();
  }

  public static class Run {
    private Map<String,String> efctByModule;
    public String description;
    public String in;
    public String out = "";
    public String err = "";
    public int exit = -1;
    public Integer stackSize = null;

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
