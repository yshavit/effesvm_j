package com.yuvalshavit.effesvm.runtime.debugger;

import java.io.Serializable;

public abstract class Msg<R extends Serializable> implements Serializable {
  private final Class<R> responseClass;

  Msg(Class<R> responseClass) {
    this.responseClass = responseClass;
  }

  abstract R process(DebuggerState state) throws InterruptedException;

  public Response<R> cast(Object o) {
    Response<?> wildResponse = (Response<?>) o;
    return Response.cast(wildResponse, responseClass);
  }

  public static abstract class NoResponse extends Msg<Ok> {
    public NoResponse() {
      super(Ok.class);
    }

    abstract void run(DebuggerState state) throws InterruptedException;

    @Override
    final Ok process(DebuggerState state) throws InterruptedException {
      run(state);
      return new Ok();
    }
  }

  public static class Ok implements Serializable {}
}
