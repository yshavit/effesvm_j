package com.yuvalshavit.effesvm.ops;

import static com.yuvalshavit.effesvm.util.ExtraAsserts.assertExceptionThrown;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.Function;

import org.testng.annotations.Test;

import com.yuvalshavit.effesvm.runtime.EffesModule;
import com.yuvalshavit.effesvm.runtime.EffesState;
import com.yuvalshavit.effesvm.runtime.OpContext;
import com.yuvalshavit.effesvm.runtime.ProgramCounter;

public class OperationFactoriesTest {

  @Test
  public void zeroArgs() {
    OperationFactories.ReflectiveOperationBuilder builder = getOpBuilder(Basic.class, "zero-args");
    assertExceptionThrown(() -> builder.build("arg1"));
    checkOp("zero-args result", builder);
  }

  @Test
  public void twoArgs() {
    OperationFactories.ReflectiveOperationBuilder builder = getOpBuilder(Basic.class, "two-args");
    assertExceptionThrown(builder::build);
    checkOp("two-args result: hello, world", builder, "hello", "world");
    assertExceptionThrown(() -> builder.build("one", "two", "three"));
  }

  @Test
  public void varArgs() {
    OperationFactories.ReflectiveOperationBuilder builder = getOpBuilder(Basic.class, "var-args");
    assertExceptionThrown(builder::build);
    checkOp("var-args: only []", builder, "only");
    checkOp("var-args: many [things, to, say]", builder, "many", "things", "to", "say");
  }

  @Test
  public void findWithinNested() {
    getOpBuilder(WithNested.class, "nested"); // reflection is tested in other things, we just want to make sure it gets found!
  }

  @Test
  public void inheritFromSuper() {
    getOpBuilder(Sub.class, "in-super");
  }

  @Test
  public void classNotPublic() {
    assertExceptionThrown(() -> OperationFactories.inClass(NotPublic.class));
  }

  @Test
  public void classNotStatic() {
    assertExceptionThrown(() -> OperationFactories.inClass(NotStatic.class));
  }

  public static class Basic {
    @OperationFactory("zero-args")
    public static Operation build0() {
      return Operation.withIncementingPc(s -> s.push("zero-args result"));
    }

    @OperationFactory("two-args")
    public static Operation build2(String first, String second) {
      return Operation.withIncementingPc(s -> s.push(String.format("two-args result: %s, %s", first, second)));
    }

    @OperationFactory("var-args")
    public static Operation build1Var(String required, String... extras) {
      return Operation.withIncementingPc(s -> s.push(String.format("var-args: %s %s", required, Arrays.toString(extras))));
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

  private static void checkOp(String expected, OperationFactories.ReflectiveOperationBuilder builder, String... args) {
    Operation op = builder.apply(Arrays.asList(args));
    EffesState state = new EffesState(ProgramCounter.end(), 10, 0);
    op.apply(new OpContext(state, new EffesModule(types, Collections.emptyMap())));
    assertEquals(state.pop(), expected);
  }

  private static OperationFactories.ReflectiveOperationBuilder getOpBuilder(Class<?> inClass, String opName) {
    Function<String,OperationFactories.ReflectiveOperationBuilder> factories = OperationFactories.inClass(inClass);
    OperationFactories.ReflectiveOperationBuilder result = factories.apply(opName);
    assertNotNull(result);
    return result;
  }
}
