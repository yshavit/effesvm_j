package com.yuvalshavit.effesvm.runtime.debugger;

import java.io.Serializable;
import java.util.function.Consumer;

import com.yuvalshavit.effesvm.util.LambdaHelpers;

public class Response<R extends Serializable> implements Serializable {
  private final R response;
  private final Throwable error;

  private Response(R response, Throwable error) {
    this.response = response;
    this.error = error;
  }

  public static <R extends Serializable> Response<R> forError(Throwable error) {
    return new Response<>(null, error);
  }

  public static <R extends Serializable> Response<R> forResponse(R response) {
    return new Response<>(response, null);
  }

  public void handle(Consumer<? super R> onSuccess, Consumer<? super Throwable> onFailure) {
    if (response != null) {
      onSuccess.accept(response);
    }
    if (error != null) {
      onFailure.accept(error);
    }
  }

  @Override
  public String toString() {
    return LambdaHelpers.consumeAndReturn(new StringBuilder(), sb -> handle(sb::append, sb::append)).toString();
  }

  public static <R extends Serializable> Response<R> cast(Response<?> response, Class<R> responseClass) {
    if (response.response == null || responseClass.isInstance(response.response)) {
      @SuppressWarnings("unchecked")
      Response<R> cast = (Response<R>) response;
      return cast;
    } else {
      throw new ClassCastException("response payload " + response + " cannot be cast to " + responseClass);
    }
  }
}
