package com.yuvalshavit.effesvm.runtime;

import java.io.IOException;
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
import java.util.stream.IntStream;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.load.EffesLoadException;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.load.Linker;
import com.yuvalshavit.effesvm.load.Parser;
import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.ops.OperationFactories;
import com.yuvalshavit.effesvm.ops.UnlinkedOperation;
import com.yuvalshavit.effesvm.util.LambdaHelpers;
import com.yuvalshavit.effesvm.util.SequencedIterator;

public class EvmRunner {

  public static final int STACK_SIZE = 500;

  private EvmRunner() {}

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      throw new IllegalArgumentException("must take at least one file, a module to run");
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
    Function<EffesState,Runnable> debug = getDebugServer();

    int exitCode = run(inputFiles, main, argsToEffes, io, null, debug);
    System.exit(exitCode);
  }

  public static int run(
    Map<EffesModule.Id,Iterable<String>> inputFiles,
    EffesModule.Id main,
    String[] argv,
    EffesIo io,
    Integer stackSize,
    Function<EffesState,Runnable> debugFactory)
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
    Runnable debug = debugFactory == null ? () -> {} : debugFactory.apply(state);

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
        debug.run();
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

  private static Function<EffesState,Runnable> getDebugServer() {
    String debug = System.getProperty("debug");
    if (debug == null) {
      return null;
    } else {
      return s -> {
        DebugServer debugServer = new DebugServer(s);
        debugServer.start(debug.equalsIgnoreCase("suspend"));
        return debugServer;
      };
    }
  }
}
