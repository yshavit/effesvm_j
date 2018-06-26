package com.yuvalshavit.effesvm.load;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

@EqualsAndHashCode
public class EfctScope implements Serializable, Comparable<EfctScope> {
  @NonNull
  @Getter
  private final EffesModule.Id moduleId;
  private final String optionalTypeName;

  private EfctScope(EffesModule.Id moduleId, String optionalTypeName) {
    this.moduleId = moduleId;
    this.optionalTypeName = optionalTypeName;
  }

  public static EfctScope ofStatic(EffesModule.Id moduleId) {
    return new EfctScope(moduleId, null);
  }

  public static EfctScope parse(String desc, EffesModule.Id context) {
    String[] descSplit = desc.split(":", 2);
    if (descSplit.length != 2) {
      throw new IllegalArgumentException("bad " + EffesFunctionId.class.getSimpleName() + ": " + desc);
    }
    EffesModule.Id module = descSplit[0].isEmpty()
      ? Objects.requireNonNull(context, "can't parse relative EffesFunctionId without context")
      : new EffesModule.Id(descSplit[0]);
    String typeDesc = descSplit[1].isEmpty() ? null : descSplit[1];
    return new EfctScope(module, typeDesc);
  }

  @Override
  public int compareTo(EfctScope o) {
    int cmp = moduleId.compareTo(o.moduleId);
    if (cmp == 0) {
      cmp = Comparator.nullsFirst(String::compareTo).compare(optionalTypeName, o.optionalTypeName);
    }
    return cmp;
  }

  @Override
  public String toString() {
    return moduleId.getName() + ':' + (optionalTypeName == null ? "" : optionalTypeName);
  }

  public <R> R map(Function<EffesModule.Id,R> ifNoType, BiFunction<EffesModule.Id,String,R> ifHasType) {
    return optionalTypeName == null
      ? ifNoType.apply(moduleId)
      : ifHasType.apply(moduleId, optionalTypeName);
  }

  public <R> R mapRequiringInstanceType(BiFunction<EffesModule.Id,String,R> f) {
    return map(
      m -> { throw new IllegalArgumentException("scope specifier " + m.getName() + ": needs a type"); },
      f);
  }

  public void listen(Consumer<EffesModule.Id> ifNoType, BiConsumer<EffesModule.Id,String> ifHasType) {
    if (optionalTypeName == null) {
      ifNoType.accept(moduleId);
    } else {
      ifHasType.accept(moduleId, optionalTypeName);
    }
  }

  public void listenRequiringInstanceType(BiConsumer<EffesModule.Id,String> f) {
    listen(
      m -> { throw new IllegalArgumentException("scope specifier " + m.getName() + ": needs a type"); },
      f);
  }
}
