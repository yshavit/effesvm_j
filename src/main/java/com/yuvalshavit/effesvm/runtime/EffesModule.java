package com.yuvalshavit.effesvm.runtime;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

public class EffesModule {
  private final Map<EffesFunction.Id,EffesFunction> functionsById;
  private final Map<String,EffesType> types;

  public EffesModule(Map<String,EffesType> types, Map<EffesFunction.Id,EffesFunction> functionsById) {
    this.types = types;
    this.functionsById = functionsById;

    // TODO will have to change with multi-module
    Set<EffesFunction.Id> functionsToUnknownTypes = functionsById.keySet().stream()
      .filter(id -> !EffesFunction.MODULE_CLASSNAME.equals(id.typeName()))
      .filter(id -> !types.containsKey(id.typeName()))
      .collect(Collectors.toSet());
    if (!functionsToUnknownTypes.isEmpty()) {
      String plural = functionsToUnknownTypes.size() == 1 ? "" : "s";
      throw new EffesLoadException(String.format("function%s defined on unknown type%s: %s", plural, plural, functionsToUnknownTypes));
    }
  }

  public EffesFunction getFunction(EffesFunction.Id id) {
    EffesFunction res = functionsById.get(id);
    if (res == null) {
      throw new NoSuchElementException(id.toString());
    }
    return res;
  }

  public EffesType getType(String typeName) {
    EffesType type = types.get(typeName);
    if (type == null) {
      throw new EffesRuntimeException("no such type: " + typeName);
    }
    return type;
  }

}
