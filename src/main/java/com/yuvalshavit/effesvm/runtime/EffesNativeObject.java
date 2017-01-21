package com.yuvalshavit.effesvm.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public abstract class EffesNativeObject extends EffesRef<EffesNativeObject.NativeType> {

  private EffesNativeObject(NativeType type) {
    super(type);
  }

  @Override
  public abstract String toString();

  @Override
  public int hashCode() {
    return equalityState().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (obj.getClass() != getClass()) {
      return false;
    }
    EffesNativeObject other = (EffesNativeObject) obj;
    return Objects.equals(equalityState(), other.equalityState());
  }

  protected abstract Object equalityState();

  public static EffesInteger forInt(int value) {
    return new EffesInteger(value);
  }

  public static EffesBoolean forBoolean(boolean value) {
    return value ? EffesBoolean.TRUE : EffesBoolean.FALSE;
  }

  public static EffesString forString(String value) {
    return new EffesString(value);
  }

  /**
   * Tries to find a regex match, and returns either an {@link EffesMatch} if the pattern was found, or {@link EffesBoolean#FALSE} if it was not.
   * The pattern is searched using {@link Matcher#find()}, <em>not</em> {@link Matcher#matches()}.
   * @param lookIn the string to look at for the pattern
   * @param pattern the regex pattern
   * @return an EffesMatch, or False
   */
  public static EffesNativeObject tryMatch(String lookIn, String pattern) {
    Pattern patternObj = Pattern.compile(pattern);
    Matcher matcher = patternObj.matcher(lookIn);
    if (matcher.find()) {
      return new EffesMatch(matcher);
    } else {
      return EffesBoolean.FALSE;
    }
  }

  public static class EffesArray extends EffesNativeObject {
    private final EffesRef<?>[] data;

    public EffesArray(int size) {
      super(NativeType.ARRAY);
      data = new EffesRef<?>[size];
      Arrays.fill(data, EffesBoolean.FALSE);
    }

    public void store(int idx, EffesRef<?> obj) {
      data[idx] = obj;
    }

    public EffesRef<?> get(int idx) {
      return data[idx];
    }

    @Override
    public String toString() {
      return String.format("Array{size=%d}", data.length);
    }

    @Override
    protected Object equalityState() {
      return Collections.unmodifiableList(Arrays.asList(data));
    }

    public int length() {
      return data.length;
    }
  }

  public static class EffesBoolean extends EffesNativeObject {
    public static final EffesBoolean TRUE = new EffesBoolean(NativeType.TRUE);
    public static final EffesBoolean FALSE = new EffesBoolean(NativeType.FALSE);

    private EffesBoolean(NativeType type) {
      super(type);
    }

    public boolean asBoolean() {
      return type() == NativeType.TRUE;
    }

    @Override
    public String toString() {
      return type().toString();
    }

    @Override
    protected Object equalityState() {
      return asBoolean();
    }
  }

  public static class EffesString extends EffesNativeObject {
    public final String value;

    private EffesString(String value) {
      super(NativeType.STRING);
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }

    @Override
    protected Object equalityState() {
      return value;
    }
  }

  public static class EffesInteger extends EffesNativeObject {
    public final int value;

    private EffesInteger(int value) {
      super(NativeType.INTEGER);
      this.value = value;
    }

    @Override
    public String toString() {
      return Integer.toString(value);
    }

    @Override
    protected Object equalityState() {
      return value;
    }
  }

  public static class EffesMatch extends EffesNativeObject {
    private final Matcher matcher;

    private EffesMatch(Matcher matcher) {
      super(NativeType.MATCH);
      this.matcher = matcher;
    }

    public EffesNativeObject group(int idx) {
      return stringOrFalse(matcher.group(idx));
    }

    public EffesNativeObject group(String name) {
      String result;
      try {
        result = matcher.group(name);
      } catch (IllegalArgumentException e) {
        result = null; // no group with such a name
      }
      return stringOrFalse(result);
    }

    public EffesInteger groupCount() {
      return EffesNativeObject.forInt(matcher.groupCount());
    }

    private EffesNativeObject stringOrFalse(String value) {
      return value == null ? EffesBoolean.FALSE : EffesNativeObject.forString(value);
    }

    @Override
    public String toString() {
      StringJoiner joiner = new StringJoiner(", ", "Match{pattern=" + matcher.pattern().pattern() + ", groups=[", "]");
      IntStream.range(1, matcher.groupCount() + 1)
        .mapToObj(matcher::group)
        .forEachOrdered(joiner::add);
      return joiner.toString();
    }

    @Override
    protected Object equalityState() {
      List<String> state = new ArrayList<>(matcher.groupCount() + 2);
      state.add(matcher.pattern().pattern());
      for (int i = 0; i <= matcher.groupCount(); ++i) {
        state.add(matcher.group(0));
      }
      return state;
    }
  }

  static class NativeType extends BaseEffesType {
    private static final NativeType TRUE = new NativeType("True");
    private static final NativeType FALSE = new NativeType("False");
    private static final NativeType INTEGER = new NativeType("Integer");
    private static final NativeType STRING = new NativeType("String");
    private static final NativeType MATCH = new NativeType("Match");
    private static final NativeType ARRAY = new NativeType("Array");

    private NativeType(String name) {
      super(name);
    }
  }
}
