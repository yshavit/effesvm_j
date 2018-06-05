package com.yuvalshavit.effesvm.runtime.debugger;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import com.yuvalshavit.effesvm.runtime.debugger.msg.Msg;

public class DebugClient implements Closeable {
  private final int port;
  private final BlockingDeque<Runnable> onClose;
  private final ConcurrentMap<Integer,ResponseHandler<?>> pendingResponses;
  private final BlockingQueue<ResponseHandler<?>> pendingMessages;
  private final AtomicReference<Socket> socketRef = new AtomicReference<>();
  private final AtomicInteger sequencer = new AtomicInteger();

  private DebugClient(int port) {
    this.port = port;
    pendingResponses = new ConcurrentHashMap<>();
    onClose = new LinkedBlockingDeque<>();
    pendingMessages = new ArrayBlockingQueue<>(64);
  }

  private void start() throws IOException {
    Socket socket = new Socket(InetAddress.getLocalHost(), port);
    if (!socketRef.compareAndSet(null, socket)) {
      throw new IllegalStateException("already started");
    }
    ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
    ObjectInputStream input = new ObjectInputStream(socket.getInputStream());

    startDaemon(() -> {
      ResponseHandler<?> msg = pendingMessages.take();
      output.writeObject(msg.message);
    });
    startDaemon(() -> {
      Object responseRaw = input.readObject();
      WithId<?> withId = (WithId<?>) responseRaw;
      ResponseHandler<?> responseHandler = pendingResponses.remove(withId.id());
      if (responseHandler == null) {
        System.err.printf("no response handler for message id %d (%s)%n", withId.id(), withId.payload());
      } else {
//        System.err.printf("handling message id %d (%s)%n", withId.id(), withId.payload());
        responseHandler.accept(withId.payload());
      }
    });
  }

  public static DebugClient start(int port) throws IOException {
    DebugClient client = new DebugClient(port);
    client.start();
    return client;
  }

  public int port() {
    return port;
  }

  private void startDaemon(IORunnable main) {
    // don't need to close when the loop ends, since the interrupt itself will have come from close()
    Thread readerThread = new Thread(main.runnableLoop(this, () -> {}));
    readerThread.setDaemon(true);
    onClose.add(readerThread::interrupt);
    readerThread.start();
  }

  public void addCloseHandler(Runnable onClose) {
    this.onClose.add(onClose);
  }

  public <R extends Serializable, M extends Msg<R>> void communicate(M message, Consumer<R> onSuccess, Consumer<Throwable> onFailure) {
    ResponseHandler<R> handler = new ResponseHandler<>(sequencer.getAndIncrement(), message, onSuccess, onFailure);
    // register it on the pendingResponses first, so that we're guaranteed to have the response handler registered before we even send the request
    pendingResponses.put(handler.message.id(), handler);
//    System.err.printf("registered message id %d: %s%n", handler.message.id(), message.getClass());
    pendingMessages.add(handler);
  }

  public <R extends Serializable, M extends Msg<R>> void communicate(M message, Consumer<R> onSuccess) {
    communicate(message, onSuccess, Throwable::printStackTrace);
  }

  @Override
  public void close() throws IOException {
    for (Iterator<Runnable> iterator = onClose.iterator(); iterator.hasNext(); ) {
      Runnable runnable = iterator.next();
      try {
        runnable.run();
      } catch (Exception e2) {
        e2.printStackTrace();
      }
      iterator.remove();
    }
    Socket socket = socketRef.getAndSet(null);
    if (socket != null) {
      socket.close();
    }
  }

  private static class ResponseHandler<R extends Serializable> implements Consumer<Object> {
    final Consumer<R> onSuccess;
    final Consumer<Throwable> onError;
    private final WithId<Msg<R>> message;

    public ResponseHandler(int id, Msg<R> message, Consumer<R> onSuccess, Consumer<Throwable> onError) {
      this.message = new WithId<>(id, message);
      this.onSuccess = onSuccess;
      this.onError = onError;
    }

    @Override
    public void accept(Object responseRaw) {
      Response<R> response = message.payload().cast(responseRaw);
      SwingUtilities.invokeLater(() -> response.handle(onSuccess, onError));
    }
  }
}
