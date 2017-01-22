package com.yuvalshavit.effesvm.load;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
      EffesFunction<Operation> function = new EffesFunction<>(id, unlinkedFunction.nVars(), unlinkedFunction.nArgs(), ops);
      linkingFunctions.put(id, new LinkPair(function, ops));
    }
    Map<EffesFunction.Id,EffesFunction<Operation>> linkedFunctions = new HashMap<>(linkingFunctions.size());
    for (LinkPair linkPair : linkingFunctions.values()) {
      linkedFunctions.put(linkPair.function.id(), linkPair.function);
    }
    LinkContext linkContext = new LinkContext() {
      Map<EffesFunction.Id,PcMove> moves = new HashMap<>();

      @Override
      public EffesType type(String typeName) {
        return types.get(typeName);
      }

      @Override
      public EffesFunction<?> getFunctionInfo(EffesFunction.Id function) {
        return unlinked.getFunction(function);
      }

      @Override
      public PcMove firstOpOf(EffesFunction.Id id) {
        return moves.computeIfAbsent(id, i -> PcMove.firstCallIn(linkedFunctions.get(i)));
      }
    };
    for (LinkPair linkPair : linkingFunctions.values()) {
      EffesFunction<UnlinkedOperation> unlinkedFunction = unlinked.getFunction(linkPair.function.id());
      for (int i = 0; i < unlinkedFunction.nOps(); ++i) {
        UnlinkedOperation unlinkedOp = unlinkedFunction.opAt(i);
        Operation linkedOp = unlinkedOp.apply(linkContext);
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
}
