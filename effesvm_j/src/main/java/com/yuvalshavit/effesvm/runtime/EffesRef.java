package com.yuvalshavit.effesvm.runtime;

public abstract class EffesRef<T extends BaseEffesType> {
  private final T type;

  protected EffesRef(T type) {
    this.type = type;
  }

  public T type() {
    return type;
  }

  public final <V extends EffesRefVisitor> V visit(V visitor) {
    visitor.start(type());
    visitAttrs(visitor);
    visitor.end();
    return visitor;
  }

  protected abstract void visitAttrs(EffesRefVisitor visitor);

  @Override
  public String toString() {
    return visit(new EffesRefFormats.Inline(false)).toString();
  }
}
