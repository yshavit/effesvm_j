package com.yuvalshavit.effesvm.runtime;

import java.util.Map;

import com.yuvalshavit.effesvm.ops.Operation;

public class EffesModule {
  private final Map<String,EffesFunction> functionsByName;

  public EffesModule(Map<String,EffesFunction> functionsByName) {
    this.functionsByName = functionsByName;
  }


}
