package com.yuvalshavit.effesvm.runtime.debugger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;

import com.yuvalshavit.effesvm.runtime.DebugServer;
import com.yuvalshavit.effesvm.runtime.EffesState;

public class SockDebugServer implements DebugServer {
  private final DebuggerState state;
  private volatile Socket socket;
  private volatile boolean active;

  public SockDebugServer(boolean suspend) {
    this.state = new DebuggerState();
    if (suspend) {
      state.suspend();
    }
  }

  public void start() throws IOException {
    active = true;
    ServerSocket serverSocket = new ServerSocket(0);
    Thread readerThread = new Thread(() -> {
      while (active) {
        Socket localLocket;
        ObjectInputStream input;
        ObjectOutputStream output;
        try {
          localLocket = serverSocket.accept();
          input = new ObjectInputStream(localLocket.getInputStream());
          output = new ObjectOutputStream(localLocket.getOutputStream());
        } catch (IOException e) {
          e.printStackTrace();
          return;
        }
        socket = localLocket;
        while (!localLocket.isClosed()) {
          try {
            Object inObj = input.readObject();
            Msg<?> inMsg = (Msg<?>) inObj;
            Response<?> response;
            try {
              Object responsePayload = inMsg.process(state);
              Serializable serializablePayload = (Serializable) responsePayload;
              response = Response.forResponse(serializablePayload);
            } catch (InterruptedException e) {
              e.printStackTrace();
              response = Response.forError(e);
              close();
            } catch (RuntimeException e) {
              e.printStackTrace();
              response = Response.forError(e);
            }
            output.writeObject(response);
          } catch (Exception e) {
            try {
              localLocket.close();
            } catch (Exception e2) {
              e2.printStackTrace();
            }
          }
        }
      }
    });
    readerThread.setDaemon(true);
    readerThread.start();
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
    active = false;
    Socket localSocket = socket;
    socket = null;
    if (localSocket != null) {
      localSocket.close();
    }
  }
}
