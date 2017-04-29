package com.yuvalshavit.effesvm.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public abstract class EffesNativeObject extends EffesRef<EffesNativeObject.NativeType> {

  private EffesNativeObject(NativeType type) {
    super(type);
  }

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
    if (matcher.matches()) {
      return new EffesMatch(matcher);
    } else {
      return EffesBoolean.FALSE;
    }
  }

  public static BaseEffesType parseType(String typeName) {
    return Stream.of(NativeTypeEnum.values())
      .map(e -> e.type)
      .filter(t -> t.name().equals(typeName))
      .findFirst()
      .orElseThrow(() -> new NoSuchElementException(typeName));
  }

  public static class EffesArray extends EffesNativeObject {
    private final EffesRef<?>[] data;

    public EffesArray(int size) {
      super(NativeTypeEnum.ARRAY.type);
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
    protected void visitAttrs(EffesRefVisitor visitor) {
      visitor.attributePrimitive("size", data.length);
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
    public static final EffesBoolean TRUE = new EffesBoolean(NativeTypeEnum.TRUE.type);
    public static final EffesBoolean FALSE = new EffesBoolean(NativeTypeEnum.FALSE.type);

    private EffesBoolean(NativeType type) {
      super(type);
    }

    public boolean asBoolean() {
      return type() == NativeTypeEnum.TRUE.type;
    }

    @Override
    protected void visitAttrs(EffesRefVisitor visitor) {
      // nothing
    }

    @Override
    protected Object equalityState() {
      return asBoolean();
    }
  }

  public static class EffesString extends EffesNativeObject {
    public final String value;

    private EffesString(String value) {
      super(NativeTypeEnum.STRING.type);
      this.value = value;
    }

    @Override
    protected void visitAttrs(EffesRefVisitor visitor) {
      visitor.attributePrimitive(null, value);
    }

    @Override
    protected Object equalityState() {
      return value;
    }
  }

  public static class EffesStringBuilder extends EffesNativeObject {
    public final StringBuilder sb;

    public EffesStringBuilder() {
      super(NativeTypeEnum.STRING_BUILDER.type);
      sb = new StringBuilder();
    }

    @Override
    protected void visitAttrs(EffesRefVisitor visitor) {
      visitor.attributePrimitive(null, sb);
    }

    @Override
    protected Object equalityState() {
      return sb.toString();
    }
  }

  public static abstract class EffesDelegatingObj<T> extends EffesNativeObject {
    private final T underlying;

    private EffesDelegatingObj(NativeTypeEnum type, T elem) {
      super(type.type);
      this.underlying = elem;
    }

    public T get() {
      return underlying;
    }

    @Override
    protected void visitAttrs(EffesRefVisitor visitor) {
      visitor.attributePrimitive(null, underlying);
    }

    @Override
    protected Object equalityState() {
      return this;
    }
  }

  public static class EffesStreamIn extends EffesDelegatingObj<EffesInput> {
    public EffesStreamIn(EffesInput source) {
      super(NativeTypeEnum.STREAM_IN, source);
    }
  }

  public static class EffesStreamOut extends EffesDelegatingObj<EffesOutput> {
    public EffesStreamOut(EffesOutput sink) {
      super(NativeTypeEnum.STREAM_OUT, sink);
    }
  }

  public static class EffesInteger extends EffesNativeObject {
    public final int value;

    private EffesInteger(int value) {
      super(NativeTypeEnum.INTEGER.type);
      this.value = value;
    }

    @Override
    protected void visitAttrs(EffesRefVisitor visitor) {
      visitor.attributePrimitive(null, value);
    }

    @Override
    protected Object equalityState() {
      return value;
    }
  }

  public static class EffesMatch extends EffesNativeObject {
    private final Matcher matcher;

    private EffesMatch(Matcher matcher) {
      super(NativeTypeEnum.MATCH.type);
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
    protected void visitAttrs(EffesRefVisitor visitor) {
      visitor.attributePrimitive("pattern", matcher.pattern().pattern());
      for (int i = 1; i < matcher.groupCount(); ++i) {
        visitor.attributePrimitive("group[" + i + "]", matcher.group(i));
      }
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
    NativeType(String name) {
      super(name);
    }
  }

  enum NativeTypeEnum {
    TRUE("True"),
    FALSE("False"),
    INTEGER("Integer"),
    STRING("String"),
    MATCH("Match"),
    ARRAY("Array"),
    STRING_BUILDER("StringBuilder"),
    STREAM_IN("StreamIn"),
    STREAM_OUT("StreamOut"),
    ;

    private final NativeType type;

    NativeTypeEnum(String name) {
      type = new NativeType(name);
    }

    BaseEffesType type() {
      return type;
    }
  }
}
