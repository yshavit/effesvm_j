package com.yuvalshavit.effesvm.runtime;

public abstract class EffesRuntimeException extends RuntimeException {
  protected EffesRuntimeException(String message) {
    super(message);
  }

  protected EffesRuntimeException() {}
}
