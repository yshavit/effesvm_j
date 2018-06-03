package com.yuvalshavit.effesvm.load;

import java.io.Serializable;

import lombok.Data;

@Data
public class EffesFunctionId implements Serializable {
  private final EfctScopeDesc scope;
  private final String functionName;

  public static EffesFunctionId parse(String scopeDesc, String functionName, EffesModule.Id context) {
    return new EffesFunctionId(EfctScopeDesc.parse(scopeDesc, context), functionName);
  }

  public boolean isConstructor() {
    return scope.map(m -> false, (m, t) -> t.equals(functionName));
  }

  public EffesFunctionId(EfctScopeDesc scope, String functionName) {
    this.scope = scope;
    this.functionName = functionName;
  }

  @Override
  public String toString() {
    return toString("");
  }

  public String toString(String innerText) {
    if (innerText == null) {
      innerText = "";
    }
    return String.format("%s[%s%s]", scope, functionName, innerText);
  }
}
