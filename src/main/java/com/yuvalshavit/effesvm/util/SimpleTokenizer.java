package com.yuvalshavit.effesvm.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.function.IntPredicate;

public class SimpleTokenizer implements Iterable<String> {

  private final String string;

  public SimpleTokenizer(String string) {
    this.string = string;
  }

  @Override
  public Iterator<String> iterator() {
    return tokenize(string);
  }

  public static Iterator<String> tokenize(String string) {
    return new TokenizingIterator(string);
  }

  private static class TokenizingIterator implements Iterator<String> {
    private final StringBuilder scratch;
    private final StringBuilder unicodeEscapeScratch;
    private final LookaheadIntIterator source;
    private String pending;

    public TokenizingIterator(String source) {
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
      }
      if (firstNonWhitespace == '"') {
        if (!parseUntil(c -> c == '"')) {
          throw new TokenizationException("found unterminated quote");
        }
      } else {
        source.pushBack(firstNonWhitespace);
        parseUntil(Character::isWhitespace);
      }
      pending = scratch.toString();
      return true;
    }

    private boolean parseUntil(IntPredicate endCondition) {
      scratch.setLength(0);
      while (source.hasNext()) {
        int codePoint = source.nextInt();
        if (endCondition.test(codePoint)) {
          return true;
        }
        if (codePoint == '\\') {
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
        } else {
          scratch.appendCodePoint(codePoint);
        }
      }
      return false;
    }

    private void unicodeEscape() {
      unicodeEscapeScratch.setLength(0);
      char c = escapeSequenceChar();
      if (c == '{') {
        for (c = escapeSequenceChar(); c != '}'; c = escapeSequenceChar()) {
          unicodeEscapeScratch.append(c);
        }
      } else {
        unicodeEscapeScratch.append(c);
        unicodeEscapeScratch.append(escapeSequenceChar());
        unicodeEscapeScratch.append(escapeSequenceChar());
        unicodeEscapeScratch.append(escapeSequenceChar());
      }
      int codePoint;
      try {
        codePoint = Integer.parseInt(unicodeEscapeScratch.toString(), 16);
      } catch (NumberFormatException e) {
        throw new TokenizationException("invalid unicode escape sequence");
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
    public TokenizationException(String message) {
      super(message);
    }
  }

  private static class LookaheadIntIterator implements PrimitiveIterator.OfInt {
    // I'm sure this is already somewhere, but I can't find it.
    private boolean hasLookaheadInt;
    private int lookaheadInt;
    private final OfInt delegateIter;

    public LookaheadIntIterator(OfInt delegateIter) {
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

    public void pushBack(int theInt) {
      lookaheadInt = theInt;
      hasLookaheadInt = true;
    }
  }

  public static void main(String[] args) {
    String s = "\uD83D\uDE80";
    System.out.println("s: " + s);
    System.out.println("len: " + s.length());
    System.out.println("code points: " + s.codePointCount(0, s.length()));
    s.chars().forEachOrdered(i -> System.out.println("c: " + i));
    s.codePoints().forEachOrdered(i -> System.out.println("p: " + i));

    s = "\uD83D\uDE80";
    System.out.println(s.matches("^.$"));
  }
}
