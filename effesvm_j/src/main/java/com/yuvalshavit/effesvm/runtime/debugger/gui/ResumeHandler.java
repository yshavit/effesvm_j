package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import javax.swing.SwingUtilities;

import com.yuvalshavit.effesvm.runtime.debugger.DebugClient;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgAwaitRunStateChanged;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgIsSuspended;

class ResumeHandler {
  final Collection<Runnable> onSuspend = Collections.synchronizedList(new ArrayList<>());
  final Collection<Runnable> onResume = Collections.synchronizedList(new ArrayList<>());
  final Collection<Component> enabledIffSuspended = Collections.synchronizedList(new ArrayList<>());

  final DebugClient connection;

  public ResumeHandler(DebugClient connection) {
    this.connection = connection;
    onSuspend(() -> setEnabledRecursively(enabledIffSuspended, true));
  }

  public void seeSuspend() {
    act(onSuspend);
  }

  public void seeResume() {
    act(onResume);
  }

  public void onResume(Runnable action) {
    onResume.add(action);
  }

  public void onSuspend(Runnable action) {
    onSuspend.add(action);
  }

  public void startWatching() {
    connection.communicate(new MsgIsSuspended(), this::seeStateChange);
  }

  private void seeStateChange(boolean isSuspended) {
    if (isSuspended) {
      seeSuspend();
    } else {
      seeResume();
    }
    connection.communicate(new MsgAwaitRunStateChanged(), this::seeStateChange);
  }

  private void act(Collection<Runnable> actions) {
    for (Runnable action : actions) {
      SwingUtilities.invokeLater(action);
    }
  }

  public void enabledIffSuspended(Component component) {
    enabledIffSuspended.add(component);
  }

  public void suspendGui() {
    setEnabledRecursively(enabledIffSuspended, false);
  }

  private static void setEnabledRecursively(Collection<? extends Component> components, boolean enabled) {
    // http://stackoverflow.com/a/13920371/1076640
    components.forEach(component -> {
      component.setEnabled(enabled);
      if (component instanceof Container) {
        setEnabledRecursively(Arrays.asList(((Container) component).getComponents()), enabled);
      }
    });
  }
}
