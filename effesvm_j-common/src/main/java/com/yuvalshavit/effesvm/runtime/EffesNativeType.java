package com.yuvalshavit.effesvm.runtime;

public enum EffesNativeType {
  TRUE("True"),
  FALSE("False"),
  INTEGER("Integer"),
  STRING("String"),
  MATCH("Match"),
  ARRAY("Array"),
  STRING_BUILDER("StringBuilder"),
  STREAM_IN("StreamIn"),
  STREAM_OUT("StreamOut"),
  ;

  private final String evmType;

  EffesNativeType(String evmType) {
    this.evmType = evmType;
  }

  public String getEvmType() {
    return evmType;
  }
}
