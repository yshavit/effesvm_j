package com.yuvalshavit.effesvm.runtime;

import java.util.HashMap;
import java.util.Map;

public enum EffesNativeType {
  TRUE("True"),
  FALSE("False"),
  INTEGER("Integer"),
  STRING("String"),
  MATCH("Match"),
  ARRAY("Array"),
  STRING_BUILDER("StringBuilder"),
  STREAM_IN("StreamIn"),
  STREAM_OUT("StreamOut"),;

  private static final Map<String, EffesNativeType> byEvmType;

  static {
    EffesNativeType[] values = EffesNativeType.values();
    byEvmType = new HashMap<>(values.length);
    for (EffesNativeType value : values) {
      byEvmType.put(value.evmType, value);
    }
  }

  private final String evmType;

  EffesNativeType(String evmType) {
    this.evmType = evmType;
  }

  public String getEvmType() {
    return evmType;
  }

  public static EffesNativeType tryGetFromEvmType(String type) {
    return byEvmType.get(type);
  }
}
