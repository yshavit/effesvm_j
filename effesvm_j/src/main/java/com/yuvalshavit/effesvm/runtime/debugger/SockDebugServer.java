package com.yuvalshavit.effesvm.runtime.debugger;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;

import com.yuvalshavit.effesvm.runtime.DebugServer;
import com.yuvalshavit.effesvm.runtime.DebugServerContext;
import com.yuvalshavit.effesvm.runtime.EffesState;
import com.yuvalshavit.effesvm.runtime.debugger.msg.Msg;

public class SockDebugServer implements DebugServer {
  private final DebuggerState state;
  private final DebugServerContext context;
  private final BlockingQueue<WithId<?>> pendingResponses;
  private final ReconnectingSocket socket;
  private volatile ExecutorService workerThreads;

  public static final ThreadFactory daemonThreadFactory = (r) -> {
    Thread t = new Thread(r);
    t.setDaemon(true);
    return t;
  };

  public SockDebugServer(DebugServerContext context, int port, boolean suspend) {
    this.context = context;
    socket = new ReconnectingSocket(port);
    this.state = new DebuggerState(context);
    pendingResponses = new ArrayBlockingQueue<>(64);
    if (suspend) {
      state.suspend();
    }
  }

  public void start() {
    workerThreads = Executors.newCachedThreadPool(daemonThreadFactory);
    // One thread for reading messages. As it reads each one, it creates a task that will (a) execute it and (b) put the response on the pendingResponses queue
    workerThreads.submit(IORunnable.createRunnableLoop(socket::close, socket::close, () -> {
      Object raw = socket.input().readObject();
      WithId<?> withId = (WithId<?>) raw;
      try {
        workerThreads.submit(() -> {
          Msg<?> msg = (Msg<?>) withId.payload();
          Response<?> response;
          try {
            Serializable responsePayload = msg.process(context, state);
            response = Response.forResponse(responsePayload);
          } catch (InterruptedException e) {
            response = Response.forError(e);
            Thread.currentThread().interrupt();
          } catch (Exception e) {
            response = Response.forError(e);
          }
          try {
            pendingResponses.put(withId.withPaylod(response));
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
      } catch (RejectedExecutionException e) {
        // no problem, just meant the thread was shut down
      }
    }));
    // One thread for taking responses from the pendingResponses queue and writing them back out
    workerThreads.submit(IORunnable.createRunnableLoop(socket::close, socket::close, () -> {
      WithId<?> response = pendingResponses.take();
      socket.output().writeObject(response);
    }));
  }

  @Override
  public void beforeAction(EffesState effesState) {
    try {
      state.beforeAction(effesState);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() throws IOException {
    ExecutorService workerThreads = this.workerThreads;
    if (workerThreads != null) {
      workerThreads.shutdownNow();
    }
    socket.close();
  }
}
