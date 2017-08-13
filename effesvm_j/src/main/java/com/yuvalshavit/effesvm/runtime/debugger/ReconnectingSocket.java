package com.yuvalshavit.effesvm.runtime.debugger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

public class ReconnectingSocket {
  private final int port;
  private final AtomicReference<SocketAndStreams> connection;

  public ReconnectingSocket(int port) {
    this.port = port;
    connection = new AtomicReference<>();
  }

  public ObjectInputStream input() throws IOException {
    return getOrOpen().input;
  }

  public ObjectOutputStream output() throws IOException {
    return getOrOpen().output;
  }

  private SocketAndStreams getOrOpen() throws IOException {
    SocketAndStreams ret = connection.get();
    if (ret == null) {
      synchronized (this) {
        ret = new SocketAndStreams(new ServerSocket(port));
        connection.set(ret);
      }
    }
    return ret;
  }

  public void close() throws IOException {
    SocketAndStreams current = connection.getAndSet(null);
    if (current != null) {
      current.serverSocket.close();
    }
  }

  private static class SocketAndStreams {
    final ServerSocket serverSocket;
    final ObjectInputStream input;
    final ObjectOutputStream output;

    public SocketAndStreams(ServerSocket serverSocket) throws IOException {
      this.serverSocket = serverSocket;
      Socket actualSocket = serverSocket.accept();
      input = new ObjectInputStream(actualSocket.getInputStream());
      output = new ObjectOutputStream(actualSocket.getOutputStream());
    }
  }
}
