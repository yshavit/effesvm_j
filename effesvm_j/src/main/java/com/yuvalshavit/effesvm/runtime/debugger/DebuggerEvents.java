package com.yuvalshavit.effesvm.runtime.debugger;

import java.io.IOException;
import java.io.Serializable;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import com.yuvalshavit.effesvm.runtime.debugger.msg.Msg;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgAwaitSuspended;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgIsSuspended;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgSuspend;

public class DebuggerEvents {
  private final DebugClient connection;
  private final EnumMap<Type,List<Runnable>> handlers;

  public DebuggerEvents(DebugClient connection) {
    this.connection = connection;
    handlers = new EnumMap<>(Type.class);
    for (Type eventType : Type.values()) {
      handlers.put(eventType, new CopyOnWriteArrayList<>());
    }
    connection.addCloseHandler(() -> alertHandlers(Type.CLOSED));
  }

  public void sendInitialState() {
    connection.communicate(new MsgIsSuspended(), isSuspended -> alertHandlers(isSuspended ? Type.SUSPENDED : Type.RESUMED));
  }

  public <R extends Serializable, M extends Msg<R>> void communicate(M message, Consumer<R> onSuccess) {
    connection.communicate(message, ok -> SwingUtilities.invokeLater(() -> onSuccess.accept(ok)));
  }

  public void on(Type eventType, Runnable runnable) {
    handlers.get(eventType).add(runnable);
  }

  public void requestResume(Msg.NoResponse message) {
    alertHandlers(Type.RESUME_REQUESTED);
    connection.communicate(message, ok -> {
      alertHandlers(Type.RESUMED);
      connection.communicate(new MsgAwaitSuspended(), ok2 -> alertHandlers(Type.SUSPENDED));
    });
  }

  public void requestSuspend() {
    alertHandlers(Type.SUSPEND_REQUESTED);
    connection.communicate(new MsgSuspend(), ok -> alertHandlers(Type.SUSPENDED));
  }

  public void requestConnectionClose() throws IOException {
    connection.close();
  }

  private void alertHandlers(Type eventType) {
    List<Runnable> runnables = handlers.get(eventType);
    SwingUtilities.invokeLater(() -> runnables.forEach(Runnable::run));
  }

  public enum Type {
    SUSPEND_REQUESTED,
    SUSPENDED,
    RESUME_REQUESTED,
    RESUMED,
    CLOSED,
  }
}
