package com.yuvalshavit.effesvm.runtime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MultiDebugServer implements DebugServer {
  private final List<DebugServer> delegates;

  private MultiDebugServer() {
    this.delegates = new ArrayList<>();
  }

  @Override
  public void beforeAction(EffesState state) {
    for (DebugServer delegate : delegates) {
      try {
        delegate.beforeAction(state);
      } catch (RuntimeException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public void close() throws IOException {
    IOException throwMe = null;
    for (DebugServer delegate : delegates) {
      try {
        delegate.close();
      } catch (IOException e) {
        if (throwMe == null) {
          throwMe = e;
        } else {
          throwMe.addSuppressed(e);
        }
      } catch (RuntimeException e) {
        if (throwMe == null) {
          throwMe = new IOException("while closing", e);
        } else {
          throwMe.addSuppressed(e);
        }
      }
    }
    if (throwMe != null) {
      throw throwMe;
    }
  }

  public static class Builder {
    private MultiDebugServer multi = new MultiDebugServer();

    public void add(DebugServer e) {
      if (e != null) {
        multi.delegates.add(e);
      }
    }

    public DebugServer build() {
      if (multi.delegates.isEmpty()) {
        return DebugServer.noop;
      } else if (multi.delegates.size() == 1) {
        return multi.delegates.get(0);
      } else {
        return multi;
      }
    }
  }
}
