package com.yuvalshavit.effesvm.runtime;

public abstract class EffesRuntimeException extends RuntimeException {

  public EffesRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  protected EffesRuntimeException(String message) {
    super(message);
  }

  protected EffesRuntimeException() {}
}
