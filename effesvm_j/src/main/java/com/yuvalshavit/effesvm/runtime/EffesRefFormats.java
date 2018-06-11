package com.yuvalshavit.effesvm.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class EffesRefFormats {
  private EffesRefFormats() {}

  private static final Set<BaseEffesType> noNameTypes = getNoNameTypes();

  private static Set<BaseEffesType> getNoNameTypes() {
    Set<BaseEffesType> set = new HashSet<>();
    set.add(EffesNativeObject.typeFor(EffesNativeType.STRING));
    set.add(EffesNativeObject.typeFor(EffesNativeType.INTEGER));
    return set;
  }

  public static class Inline implements EffesRefVisitor {
    private final boolean withAttrNames;
    private final Deque<AttrsSeen> hasAttrs = new ArrayDeque<>();
    private final StringBuilder sb = new StringBuilder();

    public Inline(boolean withAttrNames) {
      this.withAttrNames = withAttrNames;
    }

    @Override
    public void start(BaseEffesType type) {
      if (noNameTypes.contains(type)) {
        hasAttrs.push(AttrsSeen.NATIVE);
      } else {
        hasAttrs.push(AttrsSeen.NOT_SEEN);
        sb.append(type);
      }
    }

    @Override
    public void attribute(String name, EffesRef<?> value) {
      seeAttribute(name);
      value.visit(this);
    }

    @Override
    public void attributePrimitive(String name, Object value) {
      seeAttribute(name);
      value.toString().chars().forEach(c -> {
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
              sb.appendCodePoint(c);
            }
        }
      });
    }

    private void seeAttribute(String name) {
      if (hasAttrs.peek() == AttrsSeen.SEEN) {
        // already seen attrs at this level
        sb.append(", ");
      } else if (hasAttrs.peek() == AttrsSeen.NOT_SEEN) {
        hasAttrs.pop();
        hasAttrs.push(AttrsSeen.SEEN);
        sb.append('(');
      }
      if (withAttrNames && name != null) {
        sb.append(name).append('=');
      }
    }

    @Override
    public void end() {
      if (hasAttrs.pop() == AttrsSeen.SEEN) {
        sb.append(')');
      }
    }

    @Override
    public String toString() {
      return sb.toString();
    }
  }

  public static class Pretty implements EffesRefVisitor {
    private final int indents;
    private final StringBuilder sb;
    private int level;

    public Pretty(int indents) {
      this.indents = indents;
      this.sb = new StringBuilder();
    }

    public Pretty() {
      this(2);
    }

    @Override
    public void start(BaseEffesType type) {
      if (!noNameTypes.contains(type)) {
        sb.append(type);
      }
      ++level;
    }

    @Override
    public void attributePrimitive(String name, Object value) {
      seeAttribute(name);
      sb.append(value);
    }

    @Override
    public void attribute(String name, EffesRef<?> value) {
      seeAttribute(name);
      value.visit(this);
    }

    @Override
    public void end() {
      --level;
    }

    @Override
    public String toString() {
      return sb.toString();
    }

    private void seeAttribute(String name) {
      if (name == null) {
        return;
      }
      sb.append('\n');
      for (int i = 0, to = indents * level; i < to; ++i) {
        sb.append(' ');
      }
      sb.append(name).append(": ");
    }
  }

  private enum AttrsSeen {
    SEEN,
    NOT_SEEN,
    NATIVE,
  }
}
