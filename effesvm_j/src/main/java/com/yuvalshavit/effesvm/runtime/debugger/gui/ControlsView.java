package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.awt.Component;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.yuvalshavit.effesvm.runtime.debugger.DebugClient;
import com.yuvalshavit.effesvm.runtime.debugger.msg.Msg;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgResume;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgStepIn;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgStepOut;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgStepOver;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgSuspend;

public class ControlsView {
  private static final String runningMessage = "Running";
  private static final String resumeButtonText = "Resume";
  private static final String suspendButtonText = "Suspend";
  private static final String suspendedMessage = "Suspended";
  private static final String connectionClosedMessage = "Connection closed";

  private final DebugClient connection;
  private final Runnable onSuspend;
  private final Runnable onResume;
  private final JPanel stepButtons;
  private final JPanel resumePane;
  private final JLabel stateLabel;
  private final JButton resumeButton;

  public ControlsView(DebugClient connection, ResumeHandler resumeHandler, Runnable onSuspend, Runnable onResume) {
    this.connection = connection;
    this.onSuspend = onSuspend;
    this.onResume = onResume;

    stateLabel = new JLabel("Remote state pending");
    resumeButton = createResumeButton(stateLabel, resumeHandler);
    stepButtons = createStepButtons(stateLabel, resumeHandler);

    resumePane = new JPanel();
    resumePane.add(stateLabel);
    resumePane.add(resumeButton);
  }

  Component getStepButtons() {
    return stepButtons;
  }

  Component getResumeButtonPane() {
    return resumePane;
  }

  void showConnectionClosed() {
    stateLabel.setText(connectionClosedMessage);
    stateLabel.setEnabled(false);
    resumeButton.setEnabled(false);

  }

  private JPanel createStepButtons(JLabel stateLabel, ResumeHandler resumeHandler) {
    JPanel stepButtons = new JPanel();
    stepButtons.add(stepButton(stateLabel, resumeHandler, "In ⇲", new MsgStepIn()));
    stepButtons.add(stepButton(stateLabel, resumeHandler, "Over ↷", new MsgStepOver()));
    stepButtons.add(stepButton(stateLabel, resumeHandler, "Out ⇱", new MsgStepOut()));
    return stepButtons;
  }

  private JButton stepButton(JLabel stateLabel, ResumeHandler resumeHandler, String label, Msg.NoResponse message) {
    JButton stepOverButton = new JButton(label);
    stepOverButton.addActionListener(l -> connection.communicate(message, ok -> {
      stateLabel.setText(suspendedMessage);
      resumeHandler.suspendGui();
    }));
    resumeHandler.enabledIffSuspended(stepOverButton);
    return stepOverButton;
  }

  private JButton createResumeButton(JLabel stateLabel, ResumeHandler resumeHandler) {
    JButton resumeButton = new JButton(resumeButtonText);
    resumeButton.addActionListener(l -> {
      resumeHandler.suspendGui();
      switch (resumeButton.getText()) {
        case resumeButtonText:
          resumeButton.setText(suspendButtonText);
          stateLabel.setText("Resuming...");
          onResume.run();
          connection.communicate(new MsgResume());
          break;
        case suspendButtonText:
          resumeButton.setText(suspendButtonText);
          stateLabel.setText("Suspending...");
          connection.communicate(new MsgSuspend());
          break;
      }
    });
    resumeHandler.onResume(() -> {
      stateLabel.setText(runningMessage);
      resumeButton.setText(suspendButtonText);
      resumeButton.setEnabled(true);
    });
    resumeHandler.onSuspend(() -> {
      stateLabel.setText(suspendedMessage);
      resumeButton.setText(resumeButtonText);
      resumeButton.setEnabled(true);
      onSuspend.run();
    });
    return resumeButton;
  }
}
