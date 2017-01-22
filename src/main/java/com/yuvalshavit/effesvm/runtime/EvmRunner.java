package com.yuvalshavit.effesvm.runtime;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.Function;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.load.Parser;
import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.ops.OperationFactories;
import com.yuvalshavit.effesvm.util.SequencedIterator;

public class EvmRunner {

  public static final int STACK_SIZE = 500;

  private EvmRunner() {}

  public static int run(EffesModule module, int stackSize) {
    EffesFunction mainFunction = module.getFunction(new EffesFunction.Id("main"));
    if (mainFunction.nArgs() != 0) {
      throw new IllegalArgumentException("::main must take 0 arguments");
    }

    EffesState state = new EffesState(ProgramCounter.end(), stackSize, mainFunction.nVars());
    // if main() took any args, here is where we'd push them
    OpContext opContext = new OpContext(state, module);

    state.pc().restore(ProgramCounter.firstLineOfFunction(mainFunction));
    while (!state.pc().isAt(ProgramCounter.end())) {
      Operation op = state.pc().getOp();
      PcMove next;
      try {
        next = op.apply(opContext);
      } catch (Exception e) {
        throw new EffesRuntimeException("with pc " + state.pc(), e);
      }
      next.accept(state.pc());
    }
    EffesNativeObject.EffesInteger exitCode = (EffesNativeObject.EffesInteger) state.getFinalPop();
    return exitCode.value;
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      throw new IllegalArgumentException("must take exactly one file, an efct file");
    }

    Path path = FileSystems.getDefault().getPath(args[0]);
    Iterator<String> lines = Files.lines(path).iterator();
    EffesIo io = EffesIo.stdio();

    int exitCode = run(lines, io);
    System.exit(exitCode);
  }

  public static int run(Iterator<String> efctLines, EffesIo io) {
    return run(efctLines, io, null);
  }

  public static int run(Iterator<String> efctLines, EffesIo io, Integer stackSize) {
    Function<String,OperationFactories.ReflectiveOperationBuilder> ops = OperationFactories.fromInstance(new EffesOps(io));
    Parser parser = new Parser(ops);
    EffesModule module = parser.parse(SequencedIterator.wrap(efctLines));
    if (stackSize == null) {
      stackSize = STACK_SIZE;
    }
    return run(module, stackSize);
  }
}
