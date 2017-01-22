package com.yuvalshavit.effesvm.load;

import com.yuvalshavit.effesvm.EffesException;

public class EffesLoadException extends EffesException {
  public EffesLoadException(String msg) {
    super(msg);
  }

  public EffesLoadException(String message, Throwable cause) {
    super(message, cause);
  }
}
