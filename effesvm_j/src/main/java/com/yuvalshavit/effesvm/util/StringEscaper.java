package com.yuvalshavit.effesvm.util;

import java.util.concurrent.atomic.AtomicBoolean;

public class StringEscaper {
  private StringEscaper() {}

  public static String escape(String value) {
    StringBuilder sb = new StringBuilder(value.length() + 2);
    AtomicBoolean hasWhitespace = new AtomicBoolean();
    value.chars().forEach(c -> {
      switch (c) {
        case '\t':
          sb.append("\\t");
          break;
        case '\b':
          sb.append("\\b");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\f':
          sb.append("\\f");
          break;
        case '"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        default:
          if (Character.isISOControl(c)) {
            sb.append("\\u{");
            sb.append(Integer.toHexString(c));
            sb.append('}');
          } else {
            if (Character.isWhitespace(c)) {
              hasWhitespace.set(true);
            }
            sb.appendCodePoint(c);
          }
      }
    });
    if (hasWhitespace.get()) {
      return '\"' + sb.append('\"').toString();
    } else {
      return sb.toString();
    }
  }
}
