package com.yuvalshavit.effesvm.runtime;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import com.yuvalshavit.effesvm.load.Parser;
import com.yuvalshavit.effesvm.ops.EffesOps;
import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.ops.OperationFactories;

public class EvmRunner {

  public static final int STACK_SIZE = 500;

  private EvmRunner() {}

  public static int run(EffesModule module, int stackSize) {
    EffesFunction mainFunction = module.getFunction(new EffesFunction.Id("main"));
    if (mainFunction.nArgs() != 0) {
      throw new IllegalArgumentException("::main must take 0 arguments");
    }

    EffesState state = new EffesState(ProgramCounter.end(), stackSize, mainFunction.nVars());
    // if we had args to add, they would go here

    state.pc().restore(ProgramCounter.firstLineOfFunction(mainFunction));
    while (!state.pc().isAt(ProgramCounter.end())) {
      Operation op = state.pc().getOp();
      PcMove next = op.apply(state);
      next.accept(state.pc());
    }
    return (Integer) state.pop();
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      throw new IllegalArgumentException("must take exactly one file, an efct file");
    }
    Function<String,OperationFactories.ReflectiveOperationBuilder> ops = OperationFactories.inClass(EffesOps.class);
    Parser parser = new Parser(ops);

    Path path = FileSystems.getDefault().getPath(args[0]);
    EffesModule module = parser.parse(Files.lines(path).iterator());
    int exitCode = run(module, STACK_SIZE);
    System.exit(exitCode);
  }

}
