package com.yuvalshavit.effesvm.load;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.yuvalshavit.effesvm.runtime.EffesType;
import com.yuvalshavit.effesvm.util.LambdaHelpers;

import lombok.Data;
import lombok.NonNull;

public class EffesModule {
  private final Map<EffesFunctionId,EffesFunction> functions;
  private final Collection<EffesType> types;

  public EffesModule(Collection<EffesType> types, Collection<EffesFunction> functions) {
    Map<String,EffesType> typesByName = types.stream().collect(LambdaHelpers.groupByUniquely(EffesType::name, "type name"));
    this.types = Collections.unmodifiableCollection(new ArrayList<>(typesByName.values()));
    this.functions = functions.stream().collect(LambdaHelpers.groupByUniquely(EffesFunction::id, "function"));

    Set<String> unknownTypes = this.functions.keySet().stream()
      .flatMap(functionId -> functionId.getScope().map(m -> Stream.empty(), (m, type) -> Stream.of(type)))
      .distinct()
      .filter(typeName -> !typesByName.containsKey(typeName))
      .collect(Collectors.toSet());
    if (!unknownTypes.isEmpty()) {
      throw new IllegalArgumentException(String.format("unknown type%s: %s", unknownTypes.size() == 1 ? "" : "s", unknownTypes));
    }
  }

  public EffesFunction getFunction(EffesFunctionId id) {
    EffesFunction res = functions.get(id);
    if (res == null) {
      throw new NoSuchElementException(id.toString());
    }
    return res;
  }

  public Collection<EffesFunction> functions() {
    return Collections.unmodifiableCollection(functions.values());
  }

  @Data
  public static class Id implements Serializable, Comparable<Id> {
    @NonNull
    private final String name;

    @Override
    public String toString() {
      return name;
    }

    @Override
    public int compareTo(Id o) {
      return name.compareTo(o.name);
    }
  }
}
