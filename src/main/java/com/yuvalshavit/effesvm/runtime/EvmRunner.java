package com.yuvalshavit.effesvm.runtime;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.load.EffesLoadException;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.load.Linker;
import com.yuvalshavit.effesvm.load.Parser;
import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.ops.OperationFactories;
import com.yuvalshavit.effesvm.ops.UnlinkedOperation;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerGui;
import com.yuvalshavit.effesvm.runtime.debugger.SockDebugServer;
import com.yuvalshavit.effesvm.util.LambdaHelpers;
import com.yuvalshavit.effesvm.util.SequencedIterator;

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
        DebuggerGui.createConnectDialogue(-1);
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
          DebuggerGui.createConnectDialogue(port);
        }
      } else {
        System.err.printf("%s [port]. No other options allowed.%n", DEBUGGER_OPTION);
      }
      return;
    }

    String classpath = System.getenv().getOrDefault("EFFES_CLASSPATH", ".");
    Path classpathPath = FileSystems.getDefault().getPath(classpath);

    Map<EffesModule.Id,Iterable<String>> inputFiles = new HashMap<>(args.length);
    try (DirectoryStream<Path> efctFile = Files.newDirectoryStream(classpathPath, "*.efct")) {
      for (Path path : efctFile) {
        Iterable<String> lines = () -> {
          try {
            return Files.lines(path).iterator();
          } catch (IOException e) {
            throw new EffesLoadException("while reading " + path, e);
          }
        };
        Path relativePath = path.subpath(classpathPath.getNameCount(), path.getNameCount());
        ArrayList<String> moduleSegments = LambdaHelpers.consumeAndReturn(
          new ArrayList<>(relativePath.getNameCount()),
          list -> relativePath.forEach(segment -> list.add(segment.toString())));
        moduleSegments.set(moduleSegments.size() - 1, moduleSegments.get(moduleSegments.size() - 1).replaceAll("\\.efct$", ""));
        EffesModule.Id id = EffesModule.Id.of(moduleSegments.toArray(new String[0]));
        inputFiles.put(id, lines);
      }
    }
    EffesModule.Id main = EffesModule.Id.parse(args[0]);
    if (!inputFiles.containsKey(main)) {
      throw new IllegalArgumentException(main + " not found among " + inputFiles.keySet());
    }
    String[] argsToEffes = Arrays.copyOfRange(args, 1, args.length);

    EffesIo io = EffesIo.stdio();
    int exitCode;
    try (DebugServer debug = getDebugServer()) {
      exitCode = run(inputFiles, main, argsToEffes, io, null, debug);
    }
    System.exit(exitCode);
  }

  public static int run(
    Map<EffesModule.Id,Iterable<String>> inputFiles,
    EffesModule.Id main,
    String[] argv,
    EffesIo io,
    Integer stackSize,
    DebugServer debugServer)
  {
    // Parse and link the inputs
    Function<String,OperationFactories.ReflectiveOperationBuilder> ops = OperationFactories.fromInstance(new EffesOps(io));
    Map<EffesModule.Id,EffesModule<UnlinkedOperation>> parsed = new HashMap<>(inputFiles.size());
    Parser parser = new Parser(ops);
    for (Map.Entry<EffesModule.Id,Iterable<String>> inputFileEntry : inputFiles.entrySet()) {
      Iterator<String> inputFileLines = inputFileEntry.getValue().iterator();
      EffesModule.Id moduleId = inputFileEntry.getKey();
      EffesModule<UnlinkedOperation> unlinkedModule = parser.parse(moduleId, SequencedIterator.wrap(inputFileLines));
      parsed.put(moduleId, unlinkedModule);
    }
    Map<EffesModule.Id,EffesModule<Operation>> linkedModules = Linker.link(parsed);
    EffesModule<Operation> linkedModule = linkedModules.get(main);

    EffesFunction<Operation> mainFunction = linkedModule.getFunction(new EffesFunction.Id("main"));
    if (mainFunction.nArgs() != 1) {
      throw new EffesRuntimeException("::main must take 1 argument");
    }
    if (!mainFunction.hasRv()) {
      throw new EffesRuntimeException("::main must return a value");
    }

    // Create the stack
    if (stackSize == null) {
      stackSize = STACK_SIZE;
    }
    EffesState state = new EffesState(ProgramCounter.end(), stackSize, mainFunction.nVars() + 1); // +1 for argv

    state.pc().restore(ProgramCounter.firstLineOfFunction(mainFunction));
    // put argv in
    state.push(LambdaHelpers.consumeAndReturn(
      new EffesNativeObject.EffesArray(argv.length),
      effesArgv -> IntStream.range(0, argv.length).forEach(i -> effesArgv.store(i, EffesNativeObject.forString(argv[i])))));
    state.popToVar(0);
    // Start the main loop
    while (!state.pc().isAt(ProgramCounter.end())) {
      Operation op = null;
      PcMove next;
      try {
        debugServer.beforeAction(state);
        op = state.pc().getOp();
        next = op.apply(state);
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
    EffesNativeObject.EffesInteger exitCode = (EffesNativeObject.EffesInteger) state.getFinalPop();
    return exitCode.value;
  }

  private static DebugServer getDebugServer() {
    String debug = System.getProperty("debug");
    if (debug == null) {
      return DebugServer.noop;
    } else {
      Matcher debugOptions = Pattern.compile("(\\d+)(:suspend)?").matcher(debug);
      if (!debugOptions.matches()) {
        System.err.println("invalid debug options; not starting debugger");
        return DebugServer.noop;
      }
      final int port;
      if (debugOptions.group(1).isEmpty()) {
        port = 0;
      } else {
        try {
          port = Integer.parseInt(debugOptions.group(1));
        } catch (NumberFormatException e) {
          System.err.println("invalid debug options; not starting debugger");
          return DebugServer.noop;
        }
      }
      SockDebugServer debugServer = new SockDebugServer(port, debugOptions.group(2) != null);
      try {
        debugServer.start();
        return debugServer;
      } catch (Exception e) {
        e.printStackTrace();
        return DebugServer.noop;
      }
    }
  }
}
