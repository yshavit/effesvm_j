package com.yuvalshavit.effesvm.runtime.debugger;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.util.function.Consumer;

interface IORunnable {
  void run() throws Exception;

  default Runnable runnableLoop(Closeable resetAction, Closeable endAction, Consumer<? super Throwable> exceptionHandler) {
    return () -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          run();
        } catch (InterruptedException e) {
          try {
            endAction.close();
          } catch (IOException closeE) {
            exceptionHandler.accept(closeE);
          }
          break;
        } catch (SocketException | EOFException e) {
          try {
            resetAction.close();
          } catch (Exception closeE) {
            exceptionHandler.accept(closeE);
          }
        } catch (Exception e) {
          try {
            resetAction.close();
          } catch (Exception closeE) {
            e.addSuppressed(closeE);
          }
          exceptionHandler.accept(e);
        }
      }
    };
  }

  default Runnable runnableLoop(Closeable resetAction, Closeable endAction) {
    return runnableLoop(resetAction, endAction, Throwable::printStackTrace);
  }

  static Runnable createRunnableLoop(Closeable resetAction, Closeable endAction, IORunnable action) {
    return action.runnableLoop(resetAction, endAction);
  }

}
