package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.awt.Component;
import java.util.EnumMap;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.yuvalshavit.effesvm.runtime.debugger.DebuggerEvents;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerState;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgResume;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgResumeBase;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgStepIn;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgStepOut;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgStepOver;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgStepPast;

public class ControlsView {
  private static final String RUNNING_MESSAGE = "Running";
  private static final String RESUME_BUTTON_TEXT = "Resume";
  private static final String SUSPEND_BUTTON_TEXT = "Suspend";
  private static final String SUSPENDED_MESSAGE = "Suspended";
  private static final String CONNECTION_CLOSED_MESSAGE = "Connection closed";
  private static final String CLOSED_BUTTON_TEXT = "Closed";

  private final JPanel stepButtons;
  private final JPanel resumePane;
  private final SourceModeCoordinator sourceModeCoordinator;

  public ControlsView(SourceModeCoordinator sourceModeCoordinator, DebuggerEvents debuggerEvents) {
    this.sourceModeCoordinator = sourceModeCoordinator;
    JLabel stateLabel = new JLabel("Remote state pending");

    JButton resumeOrSuspendButton = createResumeButton(debuggerEvents);
    stepButtons = createStepButtons(debuggerEvents);

    resumePane = new JPanel();
    resumePane.add(stateLabel);
    resumePane.add(resumeOrSuspendButton);

    debuggerEvents.on(DebuggerEvents.Type.SUSPEND_REQUESTED, () -> {
      stateLabel.setText("Suspending...");
      resumeOrSuspendButton.setEnabled(false);
    });
    debuggerEvents.on(DebuggerEvents.Type.SUSPENDED, () -> {
      stateLabel.setText(SUSPENDED_MESSAGE);
      resumeOrSuspendButton.setText(RESUME_BUTTON_TEXT);
      resumeOrSuspendButton.setEnabled(true);
    });
    debuggerEvents.on(DebuggerEvents.Type.RESUME_REQUESTED, () -> {
      stateLabel.setText("Resuming...");
      resumeOrSuspendButton.setEnabled(false);
    });
    debuggerEvents.on(DebuggerEvents.Type.RESUMED, () -> {
      stateLabel.setText(RUNNING_MESSAGE);
      resumeOrSuspendButton.setText(SUSPEND_BUTTON_TEXT);
      resumeOrSuspendButton.setEnabled(true);
    });
    debuggerEvents.on(DebuggerEvents.Type.CLOSED, () -> {
      stateLabel.setText(CONNECTION_CLOSED_MESSAGE);
      stateLabel.setEnabled(false);
      resumeOrSuspendButton.setEnabled(false);
    });
  }

  Component getStepButtons() {
    return stepButtons;
  }

  Component getResumeButtonPane() {
    return resumePane;
  }

  private JPanel createStepButtons(DebuggerEvents debuggerEvents) {
    JPanel stepButtons = new JPanel();
    stepButtons.add(stepButton(debuggerEvents, "In ⇲", new MsgStepIn(), new MsgStepPast(DebuggerState.StepPast.SOURCE_COLUMN)));
    stepButtons.add(stepButton(debuggerEvents, "Over ↷", new MsgStepOver(), new MsgStepPast(DebuggerState.StepPast.SOURCE_LINE)));
    stepButtons.add(stepButton(debuggerEvents, "Out ⇱", new MsgStepOut(), new MsgStepOut()));
    return stepButtons;
  }

  private JButton stepButton(DebuggerEvents debuggerEvents, String label, MsgResumeBase ifEfct, MsgResumeBase ifSource) {
    JButton stepOverButton = new JButton(label);
    Runnable disable = () -> stepOverButton.setEnabled(false);
    debuggerEvents.on(DebuggerEvents.Type.RESUMED, disable);
    debuggerEvents.on(DebuggerEvents.Type.RESUME_REQUESTED, disable);
    debuggerEvents.on(DebuggerEvents.Type.CLOSED, disable);
    debuggerEvents.on(DebuggerEvents.Type.SUSPENDED, () -> stepOverButton.setEnabled(true));
    EnumMap<SourceModeCoordinator.Mode,MsgResumeBase> messages = new EnumMap<>(SourceModeCoordinator.Mode.class);
    messages.put(SourceModeCoordinator.Mode.EFCT, ifEfct);
    messages.put(SourceModeCoordinator.Mode.SOURCE, ifSource);
    stepOverButton.addActionListener(l -> {
      MsgResumeBase message = messages.get(sourceModeCoordinator.getMode());
      if (message == null) {
        System.err.printf("Ignoring step %s in unknown mode \"%s\"n", label.replaceAll(" .*", "").toLowerCase(), sourceModeCoordinator.getMode());
        return;
      }
      debuggerEvents.requestResume(message);
    });
    return stepOverButton;
  }

  private JButton createResumeButton(DebuggerEvents debuggerEvents) {
    JButton resumeButton = new JButton(RESUME_BUTTON_TEXT);
    debuggerEvents.on(DebuggerEvents.Type.SUSPEND_REQUESTED, () -> resumeButton.setText(SUSPEND_BUTTON_TEXT));
    debuggerEvents.on(DebuggerEvents.Type.SUSPENDED, () -> {
      resumeButton.setText(RESUME_BUTTON_TEXT);
      resumeButton.setEnabled(true);
    });
    debuggerEvents.on(DebuggerEvents.Type.RESUME_REQUESTED, () -> resumeButton.setText(SUSPEND_BUTTON_TEXT));
    debuggerEvents.on(DebuggerEvents.Type.RESUMED, () -> {
      resumeButton.setText(SUSPEND_BUTTON_TEXT);
      resumeButton.setEnabled(true);
    });
    debuggerEvents.on(DebuggerEvents.Type.CLOSED, () -> {
      resumeButton.setText(CLOSED_BUTTON_TEXT);
      resumeButton.setEnabled(false);
    });

    resumeButton.addActionListener(l -> {
      switch (resumeButton.getText()) {
        case RESUME_BUTTON_TEXT:
          debuggerEvents.requestResume(new MsgResume());
          break;
        case SUSPEND_BUTTON_TEXT:
          debuggerEvents.requestSuspend();
          break;
      }
    });
    return resumeButton;
  }
}
