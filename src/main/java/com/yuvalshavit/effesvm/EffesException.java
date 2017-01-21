package com.yuvalshavit.effesvm;

public abstract class EffesException extends RuntimeException {
  protected EffesException(String message) {
    super(message);
  }

  protected EffesException(String message, Throwable cause) {
    super(message, cause);
  }

  protected EffesException() {}
}
