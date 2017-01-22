package com.yuvalshavit.effesvm.ops;

import static com.yuvalshavit.effesvm.util.ExtraAsserts.assertExceptionThrown;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.Function;

import org.testng.annotations.Test;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.load.LinkContext;
import com.yuvalshavit.effesvm.runtime.EffesNativeObject;
import com.yuvalshavit.effesvm.runtime.EffesState;
import com.yuvalshavit.effesvm.runtime.EffesType;
import com.yuvalshavit.effesvm.runtime.PcMove;
import com.yuvalshavit.effesvm.runtime.ProgramCounter;

public class OperationFactoriesTest {

  @Test
  public void zeroArgs() {
    OperationFactories.ReflectiveOperationBuilder builder = getOpBuilder(new Basic(), "zero-args");
    assertExceptionThrown(() -> builder.build("arg1"));
    checkOp("zero-args result", builder, null);
  }

  @Test
  public void twoArgs() {
    OperationFactories.ReflectiveOperationBuilder builder = getOpBuilder(new Basic(), "two-args");
    assertExceptionThrown(builder::build);
    checkOp("two-args result: hello, world", builder, null , "hello", "world");
    assertExceptionThrown(() -> builder.build("one", "two", "three"));
  }

  @Test
  public void linking() {
    LinkContext linkContext = new LinkContext() {
      @Override
      public EffesType type(String typeName) {
        if ("link-type-name".equals(typeName)) {
          return new EffesType("the-link-type", Collections.emptyList());
        }
        throw new UnsupportedOperationException();
      }

      @Override
      public EffesFunction<?> getFunctionInfo(EffesFunction.Id function) {
        throw new UnsupportedOperationException();
      }

      @Override
      public PcMove firstOpOf(EffesFunction.Id id) {
        throw new UnsupportedOperationException();
      }
    };
    OperationFactories.ReflectiveOperationBuilder builder = getOpBuilder(new Basic(), "with-linking");
    checkOp("the-link-type", builder, linkContext);
  }

  @Test(enabled = false, description = "https://trello.com/c/owvHdZCx")
  public void findWithinNested() {
    getOpBuilder(new WithNested(), "nested"); // reflection is tested in other things, we just want to make sure it gets found!
  }

  @Test
  public void inheritFromSuper() {
    getOpBuilder(new Sub(), "in-super");
  }

  @Test(enabled = false, description = "https://trello.com/c/owvHdZCx")
  public void classNotPublic() {
    assertExceptionThrown(() -> OperationFactories.fromInstance(new NotPublic()));
  }

  @Test(enabled = false, description = "https://trello.com/c/owvHdZCx")
  public void classNotStatic() {
    assertExceptionThrown(() -> OperationFactories.fromInstance(new NotStatic()));
  }

  public static class Basic {
    @OperationFactory("zero-args")
    public Operation build0() {
      return Operation.withIncementingPc(s -> s.push(EffesNativeObject.forString("zero-args result")));
    }

    @OperationFactory("two-args")
    public static Operation build2(String first, String second) {
      return Operation.withIncementingPc(s -> s.push(EffesNativeObject.forString(String.format("two-args result: %s, %s", first, second))));
    }

    @OperationFactory("with-linking")
    public static UnlinkedOperation build3() {
      return (LinkContext linkCtx) -> {
        EffesType type = linkCtx.type("link-type-name");
        EffesNativeObject.EffesString str = EffesNativeObject.forString(type.name());
        return Operation.withIncementingPc(s -> s.push(str));
      };
    }
  }

  public static class WithNested {
    @SuppressWarnings("unused")
    public static class Nested {
      @OperationFactory("nested")
      public static Operation build() {
        throw new UnsupportedOperationException();
      }
    }
  }

  public static class Super {
    @OperationFactory("in-super")
    public static Operation build() {
      throw new UnsupportedOperationException();
    }
  }

  static class NotPublic {
    @OperationFactory("in-super")
    public static Operation build() {
      throw new UnsupportedOperationException();
    }
  }

  public class NotStatic {
    @OperationFactory("in-super")
    public Operation build() {
      throw new UnsupportedOperationException();
    }
  }

  public static class Sub extends Super {}

  private static void checkOp(String expected, OperationFactories.ReflectiveOperationBuilder builder, LinkContext linkContext, String... args) {
    UnlinkedOperation unlinked = builder.apply(Arrays.asList(args));
    Operation op = unlinked.apply(linkContext);
    EffesState state = new EffesState(ProgramCounter.end(), 10, 0);
    op.apply(state);
    assertEquals(state.pop().toString(), expected);
  }

  private static OperationFactories.ReflectiveOperationBuilder getOpBuilder(Object suiteInstance, String opName) {
    Function<String,OperationFactories.ReflectiveOperationBuilder> factories = OperationFactories.fromInstance(suiteInstance);
    OperationFactories.ReflectiveOperationBuilder result = factories.apply(opName);
    assertNotNull(result);
    return result;
  }
}
