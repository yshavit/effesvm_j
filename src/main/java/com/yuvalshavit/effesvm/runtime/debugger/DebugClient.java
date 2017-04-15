package com.yuvalshavit.effesvm.runtime.debugger;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Iterator;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

class DebugClient {
  public static final ThreadFactory daemonThreadFactory = (r) -> {
    Thread t = new Thread(r);
    t.setDaemon(true);
    return t;
  };

  final int port;
  final ObjectInputStream input;
  final ObjectOutputStream output;
  final BlockingDeque<Runnable> onClose;
  private final ExecutorService communicationExecutor;
  private final Socket socket;

  public DebugClient(int port) throws IOException {
    this.port = port;
    this.socket = new Socket(InetAddress.getLocalHost(), port);
    this.output = new ObjectOutputStream(socket.getOutputStream());
    this.input = new ObjectInputStream(socket.getInputStream());
    this.onClose = new LinkedBlockingDeque<>();
    communicationExecutor = Executors.newSingleThreadExecutor(daemonThreadFactory);
  }

  void addCloseHandler(Runnable onClose) {
    this.onClose.add(onClose);
  }

  <R extends Serializable, M extends Msg<R>> void communicate(M message, Consumer<R> onSuccess, Consumer<Throwable> onFailure) {
    communicationExecutor.submit(() -> {
      try {
        output.writeObject(message);
        Object responseObj = input.readObject();
        Response<R> response = message.cast(responseObj);
        SwingUtilities.invokeLater(() -> response.handle(onSuccess, onFailure));
      } catch (EOFException | SocketException e) {
        try {
          close();
        } catch (IOException e1) {
          e1.printStackTrace();
        }
      } catch (Exception e) {
        Response<R> response = Response.forError(e);
        SwingUtilities.invokeLater(() -> response.handle(onSuccess, onFailure));
      }
    });
  }

  <R extends Serializable, M extends Msg<R>> void communicate(M message, Consumer<R> onSuccess) {
    communicate(message, onSuccess, Throwable::printStackTrace);
  }

  public void close() throws IOException {
    communicationExecutor.shutdownNow();
    for (Iterator<Runnable> iterator = onClose.iterator(); iterator.hasNext(); ) {
      Runnable runnable = iterator.next();
      try {
        runnable.run();
      } catch (Exception e2) {
        e2.printStackTrace();
      }
      iterator.remove();
    }
    socket.close();
  }
}
