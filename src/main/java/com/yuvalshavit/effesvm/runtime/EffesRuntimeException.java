package com.yuvalshavit.effesvm.runtime;

public class EffesRuntimeException extends RuntimeException {

  public EffesRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  public EffesRuntimeException(String message) {
    super(message);
  }

  protected EffesRuntimeException() {}
}
