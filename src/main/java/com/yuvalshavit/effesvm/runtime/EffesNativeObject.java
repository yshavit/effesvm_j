package com.yuvalshavit.effesvm.runtime;

import java.util.Objects;

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

  static class NativeType extends BaseEffesType {
    public static final NativeType TRUE = new NativeType("True");
    public static final NativeType FALSE = new NativeType("False");
    public static final NativeType INTEGER = new NativeType("Integer");
    public static final NativeType STRING = new NativeType("String");

    private NativeType(String name) {
      super(name);
    }
  }
}
