package com.yuvalshavit.effesvm.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

public class SimpleTokenizer {

  private SimpleTokenizer() { }

  public static Iterator<String> tokenize(String string) {
    return new TokenizingIterator(string);
  }

  private static class TokenizingIterator implements Iterator<String> {
    private final StringBuilder scratch;
    private final StringBuilder unicodeEscapeScratch;
    private final LookaheadIntIterator source;
    private String pending;

    TokenizingIterator(String source) {
      this.source = new LookaheadIntIterator(source.codePoints().iterator());
      this.scratch = new StringBuilder(source.length());
      this.unicodeEscapeScratch = new StringBuilder(6);
      pending = null;
    }

    @Override
    public boolean hasNext() {
      if (pending != null) {
        return true;
      }

      boolean foundNonWhitespace = false;
      int firstNonWhitespace = -1;
      while (source.hasNext()) {
        firstNonWhitespace = source.nextInt();
        if (!Character.isWhitespace(firstNonWhitespace)) {
          foundNonWhitespace = true;
          break;
        }
      }
      if (!foundNonWhitespace) {
        return false;
      } else if (firstNonWhitespace == '#') {
        source.forEachRemaining((IntConsumer) i -> {});
        return false;
      }
      if (firstNonWhitespace == '"') {
        if (!parseUntil(c -> c == '"')) {
          throw new TokenizationException("found unterminated quote");
        }
      } else {
        source.pushBack(firstNonWhitespace);
        parseUntil(c -> {
          if (Character.isWhitespace(c)) {
            return true;
          } else if (c == '#') {
            source.pushBack(c); // so that the next call to hasNext will find it
            return true;
          } else {
            return false;
          }
        });
      }
      pending = scratch.toString();
      return true;
    }

    /**
     * Parses until the either the end condition is met or the stream ends.
     * @return whether the end condition was met (as compared to the stream ending)
     */
    private boolean parseUntil(IntPredicate endCondition) {
      scratch.setLength(0);
      while (source.hasNext()) {
        int codePoint = source.nextInt();
        if (endCondition.test(codePoint)) {
          return true;
        }
        if (codePoint == '\\') {
          handleEscapeSequence();
        } else {
          scratch.appendCodePoint(codePoint);
        }
      }
      return false;
    }

    private void handleEscapeSequence() {
      // escape sequence
      if (!source.hasNext()) {
        throw new TokenizationException("line may not end in backslash (\\)");
      }
      if (source.hasNext()) {
        final char escaped;
        switch (source.nextInt()) {
          case 'b':
            escaped = '\b';
            break;
          case 't':
            escaped = '\t';
            break;
          case 'n':
            escaped = '\n';
            break;
          case 'f':
            escaped = '\f';
            break;
          case 'r':
            escaped = '\r';
            break;
          case '"':
            escaped = '"';
            break;
          case '\'':
            escaped = '\'';
            break;
          case '\\':
            escaped = '\\';
            break;
          case 'u':
            escaped = 'u';
            break;
          default:
            throw new TokenizationException("illegal escape sequence");
        }
        if (escaped == 'u') {
          unicodeEscape();
        } else {
          scratch.append(escaped);
        }
      }
    }

    private void unicodeEscape() {
      unicodeEscapeScratch.setLength(0);
      final boolean fourDigitStyle;
      char c = escapeSequenceChar();
      if (c == '{') {
        for (c = escapeSequenceChar(); c != '}'; c = escapeSequenceChar()) {
          unicodeEscapeScratch.append(c);
        }
        fourDigitStyle = false;
      } else {
        unicodeEscapeScratch.append(c);
        unicodeEscapeScratch.append(escapeSequenceChar());
        unicodeEscapeScratch.append(escapeSequenceChar());
        unicodeEscapeScratch.append(escapeSequenceChar());
        fourDigitStyle = true;
      }
      int codePoint;
      try {
        codePoint = Integer.parseInt(unicodeEscapeScratch.toString(), 16);
      } catch (NumberFormatException e) {
        throw new TokenizationException("invalid unicode escape sequence");
      }

      if (fourDigitStyle && Character.isSurrogate((char) codePoint)) {
        throw new TokenizationException("surrogate pairs are not allowed");
      }
      scratch.appendCodePoint(codePoint);
    }

    private char escapeSequenceChar() {
      if (!source.hasNext()) {
        throw new TokenizationException("incomplete \\u escape sequence");
      }
      int c = source.nextInt();
      if (Character.charCount(c) != 1) {
        throw new TokenizationException("illegal escape sequence char");
      }
      return (char) c;
    }


    @Override
    public String next() {
      if (hasNext()) {
        String res = pending;
        pending = null;
        return res;
      } else {
        throw new NoSuchElementException();
      }
    }
  }

  public static class TokenizationException extends RuntimeException {
    TokenizationException(String message) {
      super(message);
    }
  }

  private static class LookaheadIntIterator implements PrimitiveIterator.OfInt {
    // I'm sure this is already somewhere, but I can't find it.
    private boolean hasLookaheadInt;
    private int lookaheadInt;
    private final OfInt delegateIter;

    LookaheadIntIterator(OfInt delegateIter) {
      hasLookaheadInt = false;
      this.delegateIter = delegateIter;
    }

    @Override
    public int nextInt() {
      if (hasLookaheadInt) {
        hasLookaheadInt = false;
        return lookaheadInt;
      } else {
        return delegateIter.nextInt();
      }
    }

    @Override
    public boolean hasNext() {
      return hasLookaheadInt || delegateIter.hasNext();
    }

    void pushBack(int theInt) {
      lookaheadInt = theInt;
      hasLookaheadInt = true;
    }
  }

  public static void main(String[] args) {
    String s = "";
    System.out.println("s: " + s);
    System.out.println("len: " + s.length());
    System.out.println("code points: " + s.codePointCount(0, s.length()));
    s.chars().forEachOrdered(i -> System.out.println("c: " + i));
    s.codePoints().forEachOrdered(i -> System.out.println("p: " + i));

    s = "\uD83D\uDE80";
    System.out.println(s.matches("^.$"));
  }
}
