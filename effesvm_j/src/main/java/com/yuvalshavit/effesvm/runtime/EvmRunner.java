package com.yuvalshavit.effesvm.runtime;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.yuvalshavit.effesvm.load.EfctScope;
import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesFunctionParser;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.load.OutlinedModule;
import com.yuvalshavit.effesvm.load.Parser;
import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.ops.OperationFactories;
import com.yuvalshavit.effesvm.runtime.coverage.CodeCoverageDebugServer;
import com.yuvalshavit.effesvm.runtime.debugger.gui.ConnectDialogue;
import com.yuvalshavit.effesvm.runtime.debugger.gui.DebuggerGui;
import com.yuvalshavit.effesvm.runtime.debugger.SockDebugServer;
import com.yuvalshavit.effesvm.util.LambdaHelpers;

public class EvmRunner {

  public static final int STACK_SIZE = 500;
  public static final String DEBUGGER_OPTION = "-d";

  private EvmRunner() {
  }

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      throw new IllegalArgumentException("must take at least one file, a module to run");
    }
    if (args[0].startsWith(DEBUGGER_OPTION)) {
      if (args.length == 1) {
        ConnectDialogue.create(-1);
      } else if (args.length == 2) {
        int port;
        try {
          port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
          System.err.println("invalid port: " + args[1]);
          System.exit(1);
          throw e;
        }
        try {
          DebuggerGui.connectTo(port);
        } catch (ConnectException e) {
          ConnectDialogue.create(port);
        }
      } else {
        System.err.printf("%s [port]. No other options allowed.%n", DEBUGGER_OPTION);
      }
      return;
    }

    String classpath = System.getenv().getOrDefault("EFFES_CLASSPATH", ".");
    Path classpathPath = FileSystems.getDefault().getPath(classpath);

    Map<EffesModule.Id,List<String>> inputFiles = new HashMap<>(args.length);
    try (DirectoryStream<Path> efctFile = Files.newDirectoryStream(classpathPath, "*.efct")) {
      for (Path path : efctFile) {
        List<String> lines = Files.lines(path).collect(Collectors.toList());
        String moduleName = path.toFile().getName().replaceAll("\\.efct$", "");
        EffesModule.Id id = new EffesModule.Id(moduleName);
        inputFiles.put(id, lines);
      }
    }
    EffesModule.Id main = new EffesModule.Id(args[0]);
    if (!inputFiles.containsKey(main)) {
      throw new IllegalArgumentException(main + " not found among " + inputFiles.keySet());
    }
    String[] argsToEffes = Arrays.copyOfRange(args, 1, args.length);

    EffesIo io = EffesIo.stdio();
    int exitCode = run(inputFiles, main, argsToEffes, io, null, EvmRunner::getDebugServer);
    System.exit(exitCode);
  }

  public static int run(
    Map<EffesModule.Id,List<String>> inputFiles,
    EffesModule.Id main,
    String[] argv,
    EffesIo io,
    Integer stackSize,
    Function<DebugServerContext,DebugServer> debugServerFactory)
  {
    Map<EffesModule.Id, EffesModule> linkedModules = parseAndLink(io, inputFiles);

    EffesModule linkedModule = linkedModules.get(main);
    EffesFunction mainFunction = linkedModule.getFunction(new EffesFunctionId(EfctScope.ofStatic(main), "main"));
    if (mainFunction.nArgs() != 1) {
      throw new EffesRuntimeException("::main must take 1 argument");
    }
    if (!mainFunction.hasRv()) {
      throw new EffesRuntimeException("::main must return a value");
    }
    EffesState state = createStack(argv, stackSize, mainFunction);
    runMain(debugServerFactory, linkedModules, state);
    EffesNativeObject.EffesInteger exitCode = (EffesNativeObject.EffesInteger) state.getFinalPop();
    return exitCode.value;
  }

  private static Map<EffesModule.Id, EffesModule> parseAndLink(EffesIo io, Map<EffesModule.Id, List<String>> inputFiles) {
    // Parse and link the inputs
    Map<EffesModule.Id,OutlinedModule> outline = new HashMap<>(inputFiles.size());
    for (Map.Entry<EffesModule.Id,List<String>> inputFileEntry : inputFiles.entrySet()) {
      List<String> inputFileLines = inputFileEntry.getValue();
      EffesModule.Id moduleId = inputFileEntry.getKey();
      OutlinedModule outlinedModule = Parser.parse(moduleId, inputFileLines);
      outline.put(moduleId, outlinedModule);
    }
    Function<String,OperationFactories.ReflectiveOperationBuilder> ops = OperationFactories.fromInstance(new EffesOpsImpl(io));
    return EffesFunctionParser.parse(outline, ops);
  }

  private static EffesState createStack(String[] argv, Integer stackSize, EffesFunction mainFunction) {
    // Create the stack
    if (stackSize == null) {
      stackSize = STACK_SIZE;
    }
    EffesState state = new EffesState(ProgramCounter.start(), stackSize, mainFunction.nVars() + 1); // +1 for argv

    state.pc().restore(ProgramCounter.firstLineOfFunction(mainFunction));
    // put argv in
    state.push(LambdaHelpers.consumeAndReturn(
      new EffesNativeObject.EffesArray(argv.length),
      effesArgv -> IntStream.range(0, argv.length).forEach(i -> effesArgv.store(i, EffesNativeObject.forString(argv[i])))));
    state.popToVar(0);
    return state;
  }

  private static void runMain(Function<DebugServerContext, DebugServer> debugServerFactory, Map<EffesModule.Id, EffesModule> linkedModules, EffesState state) {
    DebugServerContext debugServerContext = new DebugServerContext(Collections.unmodifiableMap(linkedModules));
    int steps = 1;
    try (DebugServer debugServer = debugServerFactory.apply(debugServerContext)) {
      while (!state.pc().isAt(ProgramCounter.end())) {
        Operation op = null;
        PcMove next;
        try {
          debugServer.beforeAction(state);
          op = state.pc().getOp();
          next = op.apply(state);
          ++steps;
        } catch (Exception e) {
          String message = "with pc " + state.pc();
          String lastSeenLabel = state.lastSeenLabel();
          if (lastSeenLabel != null) {
            message += " after " + lastSeenLabel;
          }
          if (op != null) {
            message += ": " + op;
          }
          throw new EffesRuntimeException(message, e);
        }
        next.accept(state.pc());
      }
    } catch (Exception e) {
      System.err.printf("Error at step %d:%n", steps);
      for (ProgramCounter.State frame : state.getStackTrace()) {
        EffesFunction function = frame.function();
        Operation functionOp = function.opAt(frame.pc());

        System.err.printf(
          " %s[#%d L.%d]: %s%n",
          function.id(),
          frame.pc(),
          functionOp.info().lineNumber(),
          LambdaHelpers.consumeAndReturn(new StringJoiner(" ").add(functionOp.info().opcode()), sj -> functionOp.info().arguments().forEach(sj::add)));
      }
      System.err.println();
      System.err.print("Due to: ");
      e.printStackTrace();
    }
  }

  private static DebugServer getDebugServer(DebugServerContext context) {
    MultiDebugServer.Builder builder = new MultiDebugServer.Builder();
    builder.add(getRemoteDebugger(context, System.getProperty("debug")));
    builder.add(getCodeCoverage(context, System.getProperty("coverage")));
    return builder.build();
  }

  private static DebugServer getCodeCoverage(DebugServerContext context, String outFile) {
    if (outFile == null || outFile.isEmpty()) {
      return null;
    } else {
      return new CodeCoverageDebugServer(context, outFile);
    }
  }

  private static DebugServer getRemoteDebugger(DebugServerContext context, String debug) {
    if (debug == null || debug.isEmpty()) {
      return null;
    } else {
      Matcher debugOptions = Pattern.compile("(\\d+)(:suspend)?").matcher(debug);
      if (!debugOptions.matches()) {
        System.err.println("invalid debug options; not starting debugger");
        return null;
      }
      final int port;
      if (debugOptions.group(1).isEmpty()) {
        port = 0;
      } else {
        try {
          port = Integer.parseInt(debugOptions.group(1));
        } catch (NumberFormatException e) {
          System.err.println("invalid debug options; not starting debugger");
          return null;
        }
      }
      SockDebugServer debugServer = new SockDebugServer(context, port, debugOptions.group(2) != null);
      try {
        debugServer.start();
        return debugServer;
      } catch (Exception e) {
        e.printStackTrace();
        return null;
      }
    }
  }
}
