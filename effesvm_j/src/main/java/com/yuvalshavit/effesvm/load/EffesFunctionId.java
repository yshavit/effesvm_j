package com.yuvalshavit.effesvm.load;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Data;

@Data
public class EffesFunctionId implements Serializable, Comparable<EffesFunctionId> {
  private final EfctScope scope;
  private final String functionName;

  public static EffesFunctionId parse(String scopeDesc, String functionName, EffesModule.Id context) {
    return new EffesFunctionId(EfctScope.parse(scopeDesc, context), functionName);
  }

  public boolean isConstructor() {
    return scope.map(m -> false, (m, t) -> t.equals(functionName));
  }

  public EffesFunctionId(EfctScope scope, String functionName) {
    this.scope = scope;
    this.functionName = functionName;
  }

  @Override
  public int compareTo(EffesFunctionId o) {
    int cmp = scope.compareTo(o.scope);
    if (cmp == 0) {
      cmp = functionName.compareTo(o.functionName);
    }
    return cmp;
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

  public static EffesFunctionId tryParse(String text) {
    String[] scopeSplit = text.split(Pattern.quote("["), 2);
    if (scopeSplit.length != 2) {
      return null;
    }
    EfctScope scope = EfctScope.parse(scopeSplit[0], null);
    Matcher functionNameMatcher = Pattern.compile("\\w+").matcher(scopeSplit[1]);
    if (!functionNameMatcher.find()) {
      return null;
    }
    String functionName = functionNameMatcher.group();
    return new EffesFunctionId(scope, functionName);
  }
}
