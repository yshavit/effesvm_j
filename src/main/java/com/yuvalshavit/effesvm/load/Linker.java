package com.yuvalshavit.effesvm.load;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;

import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.ops.UnlinkedOperation;
import com.yuvalshavit.effesvm.runtime.EffesType;
import com.yuvalshavit.effesvm.runtime.PcMove;
import com.yuvalshavit.effesvm.util.CachingBuilder;

public class Linker {
  public static Map<EffesModule.Id,EffesModule<Operation>> link(Map<EffesModule.Id,EffesModule<UnlinkedOperation>> unlinkedById) {
    // gather all the types
    Map<ScopeId,EffesType> types = new HashMap<>(unlinkedById.values()
      .stream()
      .map(EffesModule::types)
      .mapToInt(Collection::size)
      .sum());
    Map<ScopeId,Map<String,LinkPair>> linkingFunctions = new HashMap<>(unlinkedById.values()
      .stream()
      .map(EffesModule::functions)
      .mapToInt(Collection::size)
      .sum());
    Map<EffesModule.Id,Map<ScopeId,ScopeId>> functionScopesPerModule = new HashMap<>(unlinkedById.size());
    Map<EffesModule.Id,EffesModule<Operation>> linkedModules = new HashMap<>(unlinkedById.size());

    LinkContextImpl linkContext = new LinkContextImpl(types, linkingFunctions);
    for (Map.Entry<EffesModule.Id,EffesModule<UnlinkedOperation>> unlinkedEntry : unlinkedById.entrySet()) {
      EffesModule.Id moduleId = unlinkedEntry.getKey();
      EffesModule<UnlinkedOperation> unlinkedModule = unlinkedEntry.getValue();

      // gather the types
      for (EffesType type : unlinkedModule.types()) {
        ScopeId typeScopeId = linkContext.scopeIdBuilder.withType(moduleId, type.name());
        EffesType old = types.put(typeScopeId, type.withModuleId(moduleId));
        assert old == null : old;
      }

      // create placeholders for all the linked functions
      Map<ScopeId,ScopeId> scopeIds = new HashMap<>();
      functionScopesPerModule.put(moduleId, scopeIds);
      Collection<EffesFunction<Operation>> linkedFunctions = new ArrayList<>(unlinkedModule.functions().size());
      for (EffesFunction<?> unlinkedFunction : unlinkedModule.functions()) {
        EffesFunction.Id functionId = unlinkedFunction.id();
        ScopeId functionScope = functionId.hasTypeName()
          ? linkContext.scopeIdBuilder.withType(moduleId, functionId.typeName())
          : linkContext.scopeIdBuilder.withoutType(moduleId);
        functionScope = scopeIds.computeIfAbsent(functionScope, Function.identity()); // basically like String::intern

        List<Operation> ops = new ArrayList<>(unlinkedFunction.nOps());
        Map<String,LinkPair> functionsByName = linkingFunctions.computeIfAbsent(functionScope, k -> new HashMap<>());
        EffesFunction<Operation> function = new EffesFunction<>(functionId, unlinkedFunction.nVars(), unlinkedFunction.hasRv(), unlinkedFunction.nArgs(), ops);
        linkedFunctions.add(function);
        LinkPair linkPair = new LinkPair(function, ops);
        LinkPair old = functionsByName.put(functionId.functionName(), linkPair);
        assert old == null : old;
      }
      EffesModule<Operation> linkedModule = new EffesModule<>(unlinkedModule.types(), linkedFunctions);
      linkedModules.put(moduleId, linkedModule);
    }

    functionScopesPerModule.forEach((moduleId, scopeToSelf) ->
      scopeToSelf.keySet().forEach(scopeId -> {
        for (LinkPair linkPair : linkingFunctions.get(scopeId).values()) {
          EffesModule<UnlinkedOperation> module = unlinkedById.get(scopeId.module());
          EffesFunction<UnlinkedOperation> unlinkedFunction = module.getFunction(linkPair.function.id());
          linkContext.currentModule = moduleId;
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
    }));
    return linkedModules;
  }

  private static class LinkPair {
    final EffesFunction<Operation> function;
    final List<Operation> ops;

    public LinkPair(EffesFunction<Operation> function, List<Operation> ops) {
      this.function = function;
      this.ops = ops;
    }

    @Override
    public String toString() {
      return function.id().toString();
    }
  }

  private static class LinkContextImpl implements LinkContext {
    private final ScopeId.Builder scopeIdBuilder = new ScopeId.Builder();
    private final Map<ScopeId,EffesType> types;
    private final Map<ScopeId,Map<String,LinkPair>> functionsByScopeAndName;
    private final CachingBuilder<EffesFunction<Operation>,PcMove> moves = new CachingBuilder<>(PcMove::firstCallIn);
    private EffesModule.Id currentModule;
    private EffesFunction<?> currentLinkingFunctionInfo;

    public LinkContextImpl(Map<ScopeId,EffesType> types, Map<ScopeId,Map<String,LinkPair>> functionsByScopeAndName) {
      this.types = types;
      this.functionsByScopeAndName = functionsByScopeAndName;
    }

    @Override
    public ScopeId.Builder scopeIdBuilder() {
      return scopeIdBuilder;
    }

    @Override
    public EffesType type(ScopeId id) {
      if (!id.hasType()) {
        throw new IllegalArgumentException("no type: " + id);
      }
      id = resolveScope(id);
      EffesType res = types.get(id);
      if (res == null) {
        throw new NoSuchElementException("no type " + id);
      }
      return res;
    }

    @Override
    public EffesFunction<?> getFunctionInfo(ScopeId scope, String function) {
      return getSemiLinkedFunction(scope, function);

    }

    @Override
    public PcMove firstOpOf(ScopeId scopeId, String function) {
      EffesFunction<Operation> func = getSemiLinkedFunction(scopeId, function);
      return moves.get(func);
    }

    /**
     * Returns the {@code EffesFunction&lt;Operation&gt;} which will eventually represent the linked function. The function itself may not yet be linked; that
     * is, the Operations it returns may be null.
     */
    private EffesFunction<Operation> getSemiLinkedFunction(ScopeId scope, String function) {
      scope = resolveScope(scope);
      LinkPair linkPair = functionsByScopeAndName
        .getOrDefault(scope, Collections.emptyMap())
        .get(function);
      if (linkPair == null) {
        throw new NoSuchElementException(String.format("no function %s(%s)", scope, function));
      }
      return linkPair.function;
    }

    @Override
    public EffesFunction<?> getCurrentLinkingFunctionInfo() {
      return currentLinkingFunctionInfo;
    }

    private ScopeId resolveScope(ScopeId id) {
      if (id.inCurrentModule()) {
        id = id.hasType()
          ? scopeIdBuilder.withType(currentModule, id.type())
          : scopeIdBuilder.withoutType(currentModule);
      } else {
        id = scopeIdBuilder.intern(id);
      }
      return id;
    }
  }
}
