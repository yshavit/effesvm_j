package com.yuvalshavit.effesvm.ops;

import static org.testng.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.testng.annotations.Test;

import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.load.ScopeId;

public class ScopeIdTest {
  @Test
  public void sameModuleNoType() {
    check(
      ":",
      Collections.emptyList(),
      "");
  }

  @Test
  public void sameModuleWithType() {
    check(
      ":MyType",
      Collections.emptyList(),
      "MyType");
  }

  @Test
  public void simpleModuleNoType() {
    check(
      "mod1:",
      Collections.singletonList("mod1"),
      "");
  }

  @Test
  public void simpleModuleWithType() {
    check(
      "mod1:MyType",
      Collections.singletonList("mod1"),
      "MyType");
  }

  @Test
  public void nestedModuleNoType() {
    check(
      "mod1:mod2:",
      Arrays.asList("mod1", "mod2"),
      "");
  }

  @Test
  public void nestedModuleWithType() {
    check(
      "mod1:mod2:MyType",
      Arrays.asList("mod1", "mod2"),
      "MyType");
  }

  private static void check(String toParse, List<String> module, String type) {
    ScopeId actual = ScopeId.parse(toParse);
    EffesModule.Id moduleId = EffesModule.Id.of(module.toArray(new String[0]));
    assertEquals(actual, new ScopeId(moduleId, type));
  }

}
