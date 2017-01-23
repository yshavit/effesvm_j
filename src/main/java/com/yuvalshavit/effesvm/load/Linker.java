package com.yuvalshavit.effesvm.load;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.ops.UnlinkedOperation;
import com.yuvalshavit.effesvm.runtime.EffesType;
import com.yuvalshavit.effesvm.runtime.PcMove;

public class Linker {
  public static EffesModule<Operation> link(EffesModule<UnlinkedOperation> unlinked) {
    Map<String,EffesType> types = unlinked.types();
    Collection<EffesFunction<UnlinkedOperation>> unlinkedFunctions = unlinked.functions();
    Map<EffesFunction.Id,LinkPair> linkingFunctions = new HashMap<>(unlinkedFunctions.size());

    for (EffesFunction<UnlinkedOperation> unlinkedFunction : unlinkedFunctions) {
      List<Operation> ops = new ArrayList<>(unlinkedFunction.nOps());
      EffesFunction.Id id = unlinkedFunction.id();
      EffesFunction<Operation> function = new EffesFunction<>(id, unlinkedFunction.nVars(), unlinkedFunction.hasRv(), unlinkedFunction.nArgs(), ops);
      linkingFunctions.put(id, new LinkPair(function, ops));
    }
    Map<EffesFunction.Id,EffesFunction<Operation>> linkedFunctions = new HashMap<>(linkingFunctions.size());
    for (LinkPair linkPair : linkingFunctions.values()) {
      linkedFunctions.put(linkPair.function.id(), linkPair.function);
    }
    LinkContextImpl linkContext = new LinkContextImpl(types, unlinked, linkedFunctions);
    for (LinkPair linkPair : linkingFunctions.values()) {
      EffesFunction<UnlinkedOperation> unlinkedFunction = unlinked.getFunction(linkPair.function.id());
      linkContext.currentLinkingFunctionInfo = unlinkedFunction;
      for (int i = 0; i < unlinkedFunction.nOps(); ++i) {
        UnlinkedOperation unlinkedOp = unlinkedFunction.opAt(i);
        Operation linkedOp;
        try {
          linkedOp = unlinkedOp.apply(linkContext);
        } catch (Exception e) {
          String originalMessage = e.getMessage();
          String annotation = String.format("at op %d of %s", i, linkContext.currentLinkingFunctionInfo.id());
          String message = originalMessage == null
            ? annotation
            : (originalMessage + " " + annotation);
          throw new EffesLinkException(message, e);
        }
        linkPair.ops.add(linkedOp);
      }
    }
    return new EffesModule<>(types, linkedFunctions);
  }

  private static class LinkPair {
    final EffesFunction<Operation> function;
    final List<Operation> ops;

    public LinkPair(EffesFunction<Operation> function, List<Operation> ops) {
      this.function = function;
      this.ops = ops;
    }
  }

  private static class LinkContextImpl implements LinkContext {
    private final Map<String,EffesType> types;
    private final EffesModule<UnlinkedOperation> unlinked;
    private final Map<EffesFunction.Id,EffesFunction<Operation>> linkedFunctions;
    private final Map<EffesFunction.Id,PcMove> moves;
    EffesFunction<?> currentLinkingFunctionInfo;

    public LinkContextImpl(Map<String,EffesType> types, EffesModule<UnlinkedOperation> unlinked, Map<EffesFunction.Id,EffesFunction<Operation>> linked) {
      this.types = types;
      this.unlinked = unlinked;
      this.linkedFunctions = linked;
      moves = new HashMap<>();
    }

    @Override
    public EffesType type(String typeName) {
      EffesType res = types.get(typeName);
      if (res == null) {
        throw new NoSuchElementException("no type " + typeName);
      }
      return res;
    }

    @Override
    public EffesFunction<?> getFunctionInfo(EffesFunction.Id function) {
      EffesFunction<UnlinkedOperation> res = unlinked.getFunction(function);
      if (res == null) {
        throw new NoSuchElementException("no function " + function);
      }
      return res;
    }

    @Override
    public PcMove firstOpOf(EffesFunction.Id id) {
      return moves.computeIfAbsent(id, i -> PcMove.firstCallIn(linkedFunctions.get(i)));
    }

    @Override
    public EffesFunction<?> getCurrentLinkingFunctionInfo() {
      return currentLinkingFunctionInfo;
    }
  }
}
