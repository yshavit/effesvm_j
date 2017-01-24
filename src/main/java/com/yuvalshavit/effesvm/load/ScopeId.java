package com.yuvalshavit.effesvm.load;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

public class ScopeId {
  private static final String NO_TYPE = "";
  private final EffesModule.Id module;
  private final String type;

  public ScopeId(EffesModule.Id module, String type) {
    this.module = module;
    this.type = type;
  }

  public ScopeId(EffesModule.Id module) {
    this(module, NO_TYPE);
  }

  public static ScopeId parse(String idDesc) {
    int lastColon = idDesc.lastIndexOf(':');
    if (lastColon < 0) {
      throw new IllegalArgumentException("invalid function scope identifier (no colon): " + idDesc);
    } else if (lastColon == idDesc.length() - 1) {
      // "foo:" or "foo:bar:"
      return new ScopeId(EffesModule.Id.of(idDesc.split(":")), NO_TYPE);
    } else if (lastColon == 0) {
      // ":Foo"
      return new ScopeId(EffesModule.Id.of(), idDesc.substring(1));
    } else {
      String[] segments = idDesc.split(":");
      assert segments.length > 1 : Arrays.toString(segments);
      String[] moduleSegments = Stream.of(segments)
        .limit(segments.length - 1)
        .map(s -> {
          if (s.isEmpty()) {
            throw new IllegalArgumentException("invalid function scope identifier (empty segment): " + idDesc);
          }
          return s;
        })
        .toArray(String[]::new);
      String typeSegment = segments[segments.length - 1];
      if (typeSegment.isEmpty()) {
        throw new IllegalArgumentException("invalid function scope identifier (empty type segment): " + idDesc);
      }
      return new ScopeId(EffesModule.Id.of(moduleSegments), typeSegment);
    }
  }

  public boolean inCurrentModule() {
    return module.currentModulePlaceholder();
  }

  public EffesModule.Id module() {
    return module;
  }

  public String type() {
    if (!hasType()) {
      throw new IllegalStateException("no type");
    }
    return type;
  }

  public boolean hasType() {
    return !type.isEmpty();
  }

  @Override
  public String toString() {
    return toString(module, type);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ScopeId that = (ScopeId) o;
    return Objects.equals(module, that.module) && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(module, type);
  }

  public static String toString(EffesModule.Id module, String type) {
    return module.toString() + ':' + type;
  }
}
