package com.yuvalshavit.effesvm.runtime;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.load.EffesLoadException;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.load.Linker;
import com.yuvalshavit.effesvm.load.Parser;
import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.ops.OperationFactories;
import com.yuvalshavit.effesvm.ops.UnlinkedOperation;
import com.yuvalshavit.effesvm.util.SequencedIterator;

public class EvmRunner {

  public static final int STACK_SIZE = 500;

  private EvmRunner() {}

  public static int run(EffesModule<Operation> module, int stackSize) {
    EffesFunction<Operation> mainFunction = module.getFunction(new EffesFunction.Id("main"));
    if (mainFunction.nArgs() != 0) {
      throw new EffesRuntimeException("::main must take 0 arguments");
    }
    if (!mainFunction.hasRv()) {
      throw new EffesRuntimeException("::main must return a value");
    }

    EffesState state = new EffesState(ProgramCounter.end(), stackSize, mainFunction.nVars());
    // if main() took any args, here is where we'd push them

    state.pc().restore(ProgramCounter.firstLineOfFunction(mainFunction));
    while (!state.pc().isAt(ProgramCounter.end())) {
      Operation op = state.pc().getOp();
      PcMove next;
      try {
        next = op.apply(state);
      } catch (Exception e) {
        throw new EffesRuntimeException("with pc " + state.pc(), e);
      }
      next.accept(state.pc());
    }
    EffesNativeObject.EffesInteger exitCode = (EffesNativeObject.EffesInteger) state.getFinalPop();
    return exitCode.value;
  }

  public static void main(String[] args) throws IOException {
    if (args.length == 10) {
      throw new IllegalArgumentException("must take at least one file, an efct file");
    }

    Map<EffesModule.Id,Iterable<String>> inputFiles = new HashMap<>(args.length);
    EffesModule.Id main = null;
    for (String arg : args) {
      Path path = FileSystems.getDefault().getPath(arg);
      Iterable<String> lines = () -> {
        try {
          return Files.lines(path).iterator();
        } catch (IOException e) {
          throw new EffesLoadException("while reading " + arg, e);
        }
      };
      EffesModule.Id id = EffesModule.Id.of(arg.split(Pattern.quote(File.separator)));
      if (main == null) {
        main = id;
      }
      inputFiles.put(id, lines);
    }

    EffesIo io = EffesIo.stdio();

    int exitCode = run(inputFiles, main, io);
    System.exit(exitCode);
  }

  public static int run(Map<EffesModule.Id,Iterable<String>> inputFiles, EffesModule.Id main, EffesIo io) {
    return run(inputFiles, main, io, null);
  }

  public static int run(Map<EffesModule.Id,Iterable<String>> inputFiles, EffesModule.Id main, EffesIo io, Integer stackSize) {
    Function<String,OperationFactories.ReflectiveOperationBuilder> ops = OperationFactories.fromInstance(new EffesOps(io));
    Map<EffesModule.Id,EffesModule<UnlinkedOperation>> parsed = new HashMap<>(inputFiles.size());
    Parser parser = new Parser(ops);
    for (Map.Entry<EffesModule.Id,Iterable<String>> inputFileEntry : inputFiles.entrySet()) {
      Iterator<String> inputFileLines = inputFileEntry.getValue().iterator();
      EffesModule<UnlinkedOperation> unlinkedModule = parser.parse(SequencedIterator.wrap(inputFileLines));
      parsed.put(inputFileEntry.getKey(), unlinkedModule);
    }
    Map<EffesModule.Id,EffesModule<Operation>> linkedModules = Linker.link(parsed);
    EffesModule<Operation> linkedModule = linkedModules.get(main);
    if (stackSize == null) {
      stackSize = STACK_SIZE;
    }
    return run(linkedModule, stackSize);
  }
}
