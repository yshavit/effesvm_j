package com.yuvalshavit.effesvm.runtime;

import java.util.Map;

import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.ops.Operation;

public class DebugServerContext {
  private final Map<EffesModule.Id,EffesModule> modules;

  public DebugServerContext(Map<EffesModule.Id,EffesModule> modules) {
    this.modules = modules;
  }

  public Map<EffesModule.Id,EffesModule> modules() {
    return modules;
  }
}
