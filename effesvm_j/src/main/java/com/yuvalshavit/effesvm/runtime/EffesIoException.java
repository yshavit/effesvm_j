package com.yuvalshavit.effesvm.runtime;

import java.io.IOException;

public class EffesIoException extends EffesRuntimeException {
  public EffesIoException(String message, IOException cause) {
    super(message, cause);
  }
}
