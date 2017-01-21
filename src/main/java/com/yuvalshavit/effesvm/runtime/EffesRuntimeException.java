package com.yuvalshavit.effesvm.runtime;

import com.yuvalshavit.effesvm.EffesException;

public class EffesRuntimeException extends EffesException {

  public EffesRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  public EffesRuntimeException(String message) {
    super(message);
  }

  protected EffesRuntimeException() {}
}
