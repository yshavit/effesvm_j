package com.yuvalshavit.effesvm.load;

import java.util.*;
import java.util.stream.Collectors;

import com.yuvalshavit.effesvm.runtime.EffesRuntimeException;
import com.yuvalshavit.effesvm.runtime.EffesType;
import com.yuvalshavit.effesvm.util.LambdaHelpers;

public class EffesModule<T> {
  private final Map<EffesFunction.Id,EffesFunction<T>> functions;
  private final Map<String,EffesType> types;

  public EffesModule(Collection<EffesType> types, Collection<EffesFunction<T>> functions) {
    this.types = types.stream().collect(LambdaHelpers.groupByUniquely(EffesType::name, "type name"));
    this.functions = functions.stream().collect(LambdaHelpers.groupByUniquely(EffesFunction::id, "function"));
    Set<String> unknownTypes = this.functions.keySet().stream()
      .filter(EffesFunction.Id::hasTypeName)
      .map(EffesFunction.Id::typeName)
      .distinct()
      .filter(typeName -> !this.types.containsKey(typeName))
      .collect(Collectors.toSet());
    if (!unknownTypes.isEmpty()) {
      throw new IllegalArgumentException(String.format("unknown type%s: %s", unknownTypes.size() == 1 ? "" : "s", unknownTypes));
    }
  }

  public EffesFunction<T> getFunction(EffesFunction.Id id) {
    EffesFunction<T> res = functions.get(id);
    if (res == null) {
      throw new NoSuchElementException(id.toString());
    }
    return res;
  }

  Map<String,EffesType> types() {
    return Collections.unmodifiableMap(types);
  }

  Collection<EffesFunction<T>> functions() {
    return Collections.unmodifiableCollection(functions.values());
  }

  public static class Id {
    private static final Id CURRENT_MODULE = new Id(Collections.emptyList());
    private final List<String> fullId;

    private Id(List<String> fullId) {
      this.fullId = Collections.unmodifiableList(new ArrayList<>(fullId));
    }

    public static Id of() {
      return CURRENT_MODULE;
    }

    public static Id of(String... paths) {
      return new Id(Arrays.asList(paths));
    }

    public boolean currentModulePlaceholder() {
      return fullId.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Id id = (Id) o;
      return Objects.equals(fullId, id.fullId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(fullId);
    }

    @Override
    public String toString() {
      return fullId.stream().collect(Collectors.joining(":"));
    }
  }
}
