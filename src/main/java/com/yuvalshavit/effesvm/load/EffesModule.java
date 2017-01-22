package com.yuvalshavit.effesvm.load;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import com.yuvalshavit.effesvm.runtime.EffesRuntimeException;
import com.yuvalshavit.effesvm.runtime.EffesType;

public class EffesModule<T> {
  private final Map<EffesFunction.Id,EffesFunction<T>> functionsById;
  private final Map<String,EffesType> types;

  public EffesModule(Map<String,EffesType> types, Map<EffesFunction.Id,EffesFunction<T>> functionsById) {
    this.types = types;
    this.functionsById = functionsById;

    // TODO will have to change with multi-module
    Set<EffesFunction.Id> functionsToUnknownTypes = functionsById.keySet().stream()
      .filter(id -> !EffesFunction.MODULE_CLASSNAME.equals(id.typeName()))
      .filter(id -> !types.containsKey(id.typeName()))
      .collect(Collectors.toSet());
    if (!functionsToUnknownTypes.isEmpty()) {
      String plural = functionsToUnknownTypes.size() == 1 ? "" : "s";
      throw new EffesLinkException(String.format("function%s defined on unknown type%s: %s", plural, plural, functionsToUnknownTypes));
    }
  }

  public EffesFunction<T> getFunction(EffesFunction.Id id) {
    EffesFunction<T> res = functionsById.get(id);
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

  Map<String,EffesType> types() {
    return Collections.unmodifiableMap(types);
  }

  Collection<EffesFunction<T>> functions() {
    return Collections.unmodifiableCollection(functionsById.values());
  }
}
