package com.yuvalshavit.effesvm.runtime;

import java.util.Map;
import java.util.NoSuchElementException;

public class EffesModule {
  private final Map<EffesFunction.Id,EffesFunction> functionsById;

  public EffesModule(Map<EffesFunction.Id,EffesFunction> functionsById) {
    this.functionsById = functionsById;
  }

  public EffesFunction getFunction(EffesFunction.Id id) {
    EffesFunction res = functionsById.get(id);
    if (res == null) {
      throw new NoSuchElementException(id.toString());
    }
    return res;
  }

}
