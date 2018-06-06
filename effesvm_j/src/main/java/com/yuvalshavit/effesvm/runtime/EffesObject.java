package com.yuvalshavit.effesvm.runtime;

import java.util.Arrays;
import java.util.function.BiConsumer;

import com.yuvalshavit.effesvm.load.EffesModule;

public class EffesObject extends EffesRef<EffesType> {
  private static final EffesModule.Id QUEUE_MODULE = new EffesModule.Id("Queue");
  private static final String QUEUE_NAME = "Queue";
  private final EffesRef<?>[] args;

  public EffesObject(EffesType type, EffesRef<?>[] args) {
    super(type);
    this.args = Arrays.copyOf(args, args.length);
    if (args.length != type.nArgs()) {
      throw new EffesRuntimeException(String.format("wrong number of arguments: expected %d but saw %d", type.nArgs(), args.length));
    }
  }

  public EffesRef<?> getArgAt(int idx) {
    return args[idx];
  }

  public void storeArgTo(int idx, EffesRef<?> value) {
    args[idx] = value;
  }

  @Override
  public String toString() {
    // Special handling for Queue:Queue. Ugly, but makes debugging much easier!
    if (isConsListRecursive(this)) {
      StringBuilder sb = new StringBuilder(QUEUE_NAME).append('(');
      int emptyLen = sb.length();
      visitAttrsForCons((idx, value) -> sb.append(value).append(", "));
      if (sb.length() != emptyLen) {
        sb.setLength(sb.length() - 2);
      }
      sb.append(')');
      return sb.toString();
    } else {
      return super.toString();
    }
  }

  @Override
  protected void visitAttrs(EffesRefVisitor visitor) {
    if (!visitAttrsForCons(visitor::attribute)) {
      for (int i = 0; i < args.length; ++i) {
        visitor.attribute(type().argAt(i), getArgAt(i));
      }
    }
  }

  private boolean visitAttrsForCons(BiConsumer<String,EffesRef<?>> handler) {
    // special handling for Queue:Queue, to make things less indented
    // This is a bit ugly, for sure! The language should eventually provide this in a more language-oriented (not VM-special-cased) way.
    EffesType type = type();
    if (isConsType(type) && isConsListRecursive(getArgAt(1))) {
      int i = 0;
      EffesRef<?> head = this;
      while (isConsType(head.type())) {
        EffesObject headCons = (EffesObject) head;
        handler.accept("[" + i + "]", headCons.getArgAt(0));
        head = headCons.getArgAt(1);
        ++i;
      }
      return true;
    }
    return false;
  }

  private static boolean isConsType(BaseEffesType type) {
    return isEffesType(type, QUEUE_MODULE, QUEUE_NAME, "head", "tail");
  }

  private static boolean isEffesType(BaseEffesType typeToCheck, EffesModule.Id targetTypeModule, String targetTypeName, String... targetTypeArgs) {
    if (!(typeToCheck instanceof EffesType)) {
      return false;
    }
    EffesType effesType = (EffesType) typeToCheck;
    if (targetTypeModule.equals(effesType.moduleId()) && targetTypeName.equals(effesType.name()) && targetTypeArgs.length == effesType.nArgs()) {
      for (int i = 0; i < targetTypeArgs.length; ++i) {
        if (!targetTypeArgs[i].equals(effesType.argAt(i))) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  private boolean isConsListRecursive(EffesRef<?> elem) {
    if (isConsType(elem.type())) {
      EffesObject cons = (EffesObject) elem;
      return isConsListRecursive(cons.getArgAt(1));
    } else {
      return isEffesType(elem.type(), QUEUE_MODULE, "Empty");
    }
  }
}
