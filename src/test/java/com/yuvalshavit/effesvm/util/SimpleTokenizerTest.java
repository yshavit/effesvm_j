package com.yuvalshavit.effesvm.util;

import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SimpleTokenizerTest {

  @Test(dataProvider = "good-tokens")
  public void valid(String input, List<String> expectedTokens) {
    Iterator<String> tokenized = SimpleTokenizer.tokenize(input);
    List<String> actual = new ArrayList<>();
    tokenized.forEachRemaining(actual::add);
    assertEquals(actual, expectedTokens);
  }

  @DataProvider(name = "good-tokens")
  public Object[][] goodParams() {
    return new Object[][] {
      new Object[] { "FUNC : main 0 0 0", Arrays.asList("FUNC", ":", "main", "0", "0", "0")},
      new Object[] { "", Collections.emptyList()},
      new Object[] { "         ", Collections.emptyList()},

      new Object[] { "hello world", Arrays.asList("hello", "world")},
      new Object[] { "hello \"world there\"", Arrays.asList("hello", "world there")},
      new Object[] { "    hello", Collections.singletonList("hello")},
      new Object[] { "hello      ", Collections.singletonList("hello")},

      new Object[] { "bareword 🚀 rocket", Arrays.asList("bareword", "🚀", "rocket")},
      new Object[] { "bareword \\u{1F680} rocket", Arrays.asList("bareword", "🚀", "rocket")},
      new Object[] { "bareword \\u2603 snowman", Arrays.asList("bareword", "☃", "snowman")},

      new Object[] { "quoted \"zoom 🚀 rocket\"", Arrays.asList("quoted", "zoom 🚀 rocket")},
      new Object[] { "quoted \"zoom \\u{1F680} rocket\"", Arrays.asList("quoted", "zoom 🚀 rocket")},
      new Object[] { "quoted \"snowy \\u2603 snowman\"", Arrays.asList("quoted", "snowy ☃ snowman")},

      new Object[] { "bareword \\n escape", Arrays.asList("bareword", "\n", "escape")},
      new Object[] { "quoted \"line \\n escape\"", Arrays.asList("quoted", "line \n escape")},

      new Object[] { "# comment line", Collections.emptyList()},
      new Object[] { "bareword then #comment line", Arrays.asList("bareword", "then")},
      new Object[] { "bareword then# comment line without a space", Arrays.asList("bareword", "then")},
      new Object[] { "quoted \"then # a hash\"", Arrays.asList("quoted", "then # a hash")},
    };
  }

  @Test(dataProvider = "bad-tokens", expectedExceptions = SimpleTokenizer.TokenizationException.class)
  public void invalid(String input) {
    SimpleTokenizer.tokenize(input).forEachRemaining(s -> {});
  }

  @DataProvider(name = "bad-tokens")
  public Object[][] badParams() {
    return new Object[][] {
      new Object[] { "invalid escape \\P"},
      new Object[] { "ends in escape \\"},
      new Object[] { "incomplete old-style unicode \\u260"},
      new Object[] { "unclosed new-style unicode \\u{2603"},
      new Object[] { "invalid old-style unicode \\uZEBRA"},
      new Object[] { "invalid new-style unicode \\u{ZEBRA}"},
      new Object[] { "unclosed \"quotation mark"},
      new Object[] { "java-style \"continuation \\uD83D\\uDE80 sequences\""},
      new Object[] { "java-style \"continuation \\uD83D sequences (just high)\""},
      new Object[] { "java-style \"continuation \\uDE80 sequences (just low)\""},
    };
  }
}
