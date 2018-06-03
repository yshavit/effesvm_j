package com.yuvalshavit.effesvm.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.regex.Pattern;

import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.Yaml;

import com.google.common.base.Charsets;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.load.Parser;
import com.yuvalshavit.effesvm.runtime.DebugServer;
import com.yuvalshavit.effesvm.runtime.EffesInput;
import com.yuvalshavit.effesvm.runtime.EffesIo;
import com.yuvalshavit.effesvm.runtime.EffesOutput;
import com.yuvalshavit.effesvm.runtime.EvmRunner;

public class EndToEndTest {
  private static final String DEFAULT_MODULE_NAME = "main";

  @DataProvider(name = "tests")
  public Iterator<Object[]> tests() {
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
      } catch (Exception e) {
        System.err.println("for file: " + path);
        e.printStackTrace();
      }
    }
    return tests.iterator();
  }

  @Test(dataProvider = "tests")
  public void run(Run run) {
    Map<EffesModule.Id,List<String>> modules = new HashMap<>(run.efctByModule.size());
    for (Map.Entry<String,String> efctByModule : run.efctByModule.entrySet()) {
      EffesModule.Id module = new EffesModule.Id(efctByModule.getKey());
      String[] lines = efctByModule.getValue().split("\\n");
      List<String> efct = new ArrayList<>(lines.length + 1);
      efct.add(Parser.EFCT_0_HEADER);
      Collections.addAll(efct, lines);
      modules.put(module, efct);
    }

    InMemoryIo io = new InMemoryIo(run.in, run.filesIn);
    int exitCode = EvmRunner.run(modules, new EffesModule.Id(DEFAULT_MODULE_NAME), run.args, io, run.stackSize, c -> DebugServer.noop);
    assertEquals(exitCode, run.exit, "exit code");
    assertEquals(io.out.toString().trim(), run.out.trim(), "stdout");
    assertEquals(io.err.toString().trim(), run.err.trim(), "stderr");
    assertEquals(io.filesWritten, run.filesOut, "files written");
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
    public Map<String,String> filesIn = Collections.emptyMap();
    public Map<String,String> filesOut = Collections.emptyMap();
    public Integer stackSize = null;
    public String[] args = new String[0];

    @Override
    public String toString() {
      return description == null ? super.toString() : description;
    }
  }

  private static class InMemoryIo implements EffesIo {
    private final Iterator<String> in;
    private final StringBuilder out;
    private final StringBuilder err;
    private final Map<String,String> filesRead;
    private final Map<String,String> filesWritten;

    private InMemoryIo(String in, Map<String,String> filesRead) {
      this.filesRead = filesRead;
      this.filesWritten = new TreeMap<>();
      List<String> inLines = in == null ? Collections.emptyList() : Arrays.asList(in.split("\\n"));
      this.in = inLines.iterator();
      out = new StringBuilder();
      err = new StringBuilder();
    }

    @Override
    public EffesInput in() {
      return () -> in.hasNext() ? in.next() : null;
    }

    @Override
    public EffesOutput out() {
      return out::append;
    }

    @Override
    public EffesOutput err() {
      return err::append;
    }

    @Override
    public InputStream readFile(String name) {
      String contents = filesRead.get(name);
      assertNotNull(contents, name);
      return new ByteArrayInputStream(contents.getBytes(Charsets.UTF_8));
    }

    @Override
    public OutputStream writeFile(String name) {
      return new OutputStream() {
        @Override
        public void write(int b) throws IOException {
          if (b > 127 || b < 0) {
            throw new IOException(String.format("byte out of ASCII range: %d (%s)", b, (char) b));
          }
          String old = filesWritten.get(name);
          if (old == null) {
            old = "";
          }
          String updated = old + ((char) b);
          filesWritten.put(name, updated);
        }
      };
    }
  }
}
