package com.yuvalshavit.effesvm.load;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.yuvalshavit.effesvm.ops.LabelUnlinkedOperation;
import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.ops.OperationFactories;
import com.yuvalshavit.effesvm.ops.UnlinkedOperation;
import com.yuvalshavit.effesvm.ops.VarUnlinkedOperation;
import com.yuvalshavit.effesvm.runtime.EffesType;

public class EffesFunctionParser {
  private static final Pattern sourceDebugInfoPattern = Pattern.compile("^(\\d+):(\\d+)");

  private EffesFunctionParser() {
  }

  public static Map<EffesModule.Id, EffesModule> parse(
    Map<EffesModule.Id, OutlinedModule> outline,
    Function<String, OperationFactories.ReflectiveOperationBuilder> ops)
  {
    Map<EffesFunctionId, FunctionAllocation> functionsById = outline
      .values()
      .stream()
      .map(OutlinedModule::getFunctions)
      .map(Map::entrySet)
      .flatMap(Collection::stream)
      .collect(Collectors.toMap(Map.Entry::getKey, FunctionAllocation::new));
    BiFunction<EffesModule.Id, String, EffesType> typeLookup = (moduleId, typeName) -> {
      OutlinedModule module = outline.get(moduleId);
      if (module == null) {
        throw new NoSuchElementException(moduleId.toString());
      }
      return module.getTypes().get(typeName);
    };
    functionsById.forEach((functionId, allocation) -> {
      List<EfctLine> lines = allocation.parse.getLines();
      List<UnlinkedOperation> unlinkedOps = new ArrayList<>(lines.size());
      Map<String, Integer> labelsMap = new HashMap<>();
      int totalNVars = allocation.parse.getNArgs();
      Matcher sourceDebugInfoMatcher = sourceDebugInfoPattern.matcher("");
      for (EfctLine line : lines) {
        String firstWord = line.get(0, "first word");
        final int sourceLine;
        final int sourcePosInLine;
        final int opArgsIndex;
        final String opcode;
        if (sourceDebugInfoMatcher.reset(firstWord).matches()) {
          sourceLine = Integer.parseInt(sourceDebugInfoMatcher.group(1));
          sourcePosInLine = Integer.parseInt(sourceDebugInfoMatcher.group(2));
          opcode = line.get(1, "opcode");
          opArgsIndex = 2;
        } else {
          sourceLine = -1;
          sourcePosInLine = -1;
          opcode = firstWord;
          opArgsIndex = 1;
        }
        OperationFactories.ReflectiveOperationBuilder opBuilder = ops.apply(opcode);
        if (opBuilder == null) {
          throw new EffesLoadException("no such op: " + opcode);
        }
        UnlinkedOperation unlinked = opBuilder.build(
          functionId.getScope().getModuleId(),
          line.getLineNum(),
          sourceLine,
          sourcePosInLine,
          line.tailTokens(opArgsIndex));
        if (unlinked instanceof LabelUnlinkedOperation) {
          String label = ((LabelUnlinkedOperation) unlinked).label();
          labelsMap.put(label, unlinkedOps.size());
        } else if (unlinked instanceof VarUnlinkedOperation) {
          int varIndex = ((VarUnlinkedOperation) unlinked).varIndex();
          totalNVars = Math.max(totalNVars, varIndex + 1);
        }
        unlinkedOps.add(unlinked);
      }
      LinkContext linkContext = new LinkContextImpl(
        functionId.getScope().getModuleId(),
        fid -> functionsById.containsKey(fid)
          ? functionsById.get(fid).allocated
          : null,
        typeLookup,
        unlinkedOps.size(),
        labelsMap);
      List<Operation> linked = unlinkedOps.stream().map(unlinked -> unlinked.apply(linkContext)).collect(Collectors.toList());
      allocation.allocated.setOps(linked);
      allocation.allocated.setNVars(totalNVars - allocation.parse.getNArgs());
    });

    return outline.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
      OutlinedModule outlinedModule = e.getValue();
      List<EffesFunction> fs = outlinedModule.getFunctions().keySet().stream().map(fid -> functionsById.get(fid).allocated).collect(Collectors.toList());
      return new EffesModule(outlinedModule.getTypes().values(), fs);
    }));
  }

  private static class FunctionAllocation {
    final OutlinedModule.FunctionParse parse;
    final EffesFunction allocated;

    public FunctionAllocation(Map.Entry<EffesFunctionId, OutlinedModule.FunctionParse> entry) {
      parse = entry.getValue();
      allocated = new EffesFunction(entry.getKey(), parse.isHasReturnValue(), parse.getNArgs());
    }
  }

  private static class LinkContextImpl implements LinkContext {

    private final Function<EffesFunctionId, EffesFunction> functions;
    private final BiFunction<EffesModule.Id, String, EffesType> types;
    private final int nOps;
    private final Map<String, Integer> labelsMap;
    private final EffesModule.Id currentModule;

    public LinkContextImpl(
      EffesModule.Id currentModule,
      Function<EffesFunctionId, EffesFunction> functions,
      BiFunction<EffesModule.Id, String, EffesType> types,
      int nOps,
      Map<String, Integer> labelsMap)
    {
      this.currentModule = currentModule;
      this.functions = functions;
      this.types = types;
      this.nOps = nOps;
      this.labelsMap = labelsMap;
    }

    @Override
    public EffesModule.Id currentModule() {
      return currentModule;
    }

    @Override
    public EffesType type(EffesModule.Id id, String typeName) {
      return types.apply(id, typeName);
    }

    @Override
    public EffesFunction getFunctionInfo(EffesFunctionId id) {
      return functions.apply(id);
    }

    @Override
    public int nOpsInCurrentFunction() {
      return nOps;
    }

    @Override
    public int findLabelOpIndex(String label) {
      Integer rv = labelsMap.get(label);
      if (rv == null) {
        throw new NoSuchElementException(label);
      }
      return rv;
    }
  }

}
