package com.yuvalshavit.effesvm.load;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import com.yuvalshavit.effesvm.util.SimpleTokenizer;

class EfctLine {
  private final String[] tokens; // null if the line was actually empty, 0-element if a comment
  private final int lineNum;

  EfctLine(String line, int lineNum) {
    this.lineNum = lineNum;
    line = line.trim();
    if (line.isEmpty()) {
      tokens = null;
    } else {
      List<String> tokensList = new ArrayList<>();
      SimpleTokenizer.tokenize(line).forEachRemaining(tokensList::add);
      this.tokens = tokensList.toArray(new String[tokensList.size()]);
    }
  }

  public int getLineNum() {
    return lineNum;
  }

  public int nTokens() {
    return tokens.length;
  }

  /** the line was literally empty, other than whitespace */
  public boolean isEmpty() {
    return tokens == null;
  }

  /** the line was either empty, or entirely a comment */
  boolean isEmptyOrComment() {
    return tokens == null || tokens.length == 0;
  }

  <T> T get(int idx, String attrDescription, Function<String,T> tokenParser) {
    String tokenText;
    try {
      tokenText = tokens[idx];
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new IllegalArgumentException("missing " + attrDescription);
    }
    try {
      return tokenParser.apply(tokenText);
    } catch (Exception e) {
      throw new IllegalArgumentException("invalid " + attrDescription, e);
    }
  }

  String get(int idx, String attrDescription) {
    return get(idx, attrDescription, Function.identity());
  }

  String[] tailTokens(int from) {
    return Arrays.copyOfRange(tokens, from, tokens.length);
  }

  @Override
  public String toString() {
    return lineNum + ": " + Arrays.toString(tokens);
  }
}
