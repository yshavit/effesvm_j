package com.yuvalshavit.effesvm.runtime;

public class EffesNativeType extends BaseEffesType {
  public static final EffesNativeType TRUE = new EffesNativeType("True");
  public static final EffesNativeType FALSE = new EffesNativeType("False");
  public static final EffesNativeType INTEGER = new EffesNativeType("Integer");
  public static final EffesNativeType STRING = new EffesNativeType("String");


  private EffesNativeType(String name) {
    super(name);
  }
}
