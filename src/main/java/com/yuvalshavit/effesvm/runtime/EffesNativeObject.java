package com.yuvalshavit.effesvm.runtime;

import java.util.Objects;

public abstract class EffesNativeObject extends EffesRef<EffesNativeType> {

  private EffesNativeObject(EffesNativeType type) {
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
    public static final EffesBoolean TRUE = new EffesBoolean(EffesNativeType.TRUE);
    public static final EffesBoolean FALSE = new EffesBoolean(EffesNativeType.FALSE);

    private EffesBoolean(EffesNativeType type) {
      super(type);
    }

    public boolean asBoolean() {
      return type() == EffesNativeType.TRUE;
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
      super(EffesNativeType.STRING);
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
      super(EffesNativeType.INTEGER);
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
}
