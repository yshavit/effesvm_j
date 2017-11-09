package com.yuvalshavit.effesvm.runtime;

public enum EffesNativeType {
  STRING("String");

  private final String evmType;

  EffesNativeType(String evmType) {
    this.evmType = evmType;
  }

  public String getEvmType() {
    return evmType;
  }
}
