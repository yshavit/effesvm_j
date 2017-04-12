package com.yuvalshavit.effesvm.runtime.debugger;

import java.io.Serializable;

public class MsgHello extends Msg<MsgHello.Response> {
  public MsgHello() {
    super(Response.class);
  }

  @Override
  Response process(DebuggerState state) {
    return new Response();
  }

  public static class Response implements Serializable {
  }
}
