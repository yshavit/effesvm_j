package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.runtime.debugger.DebugClient;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerGuiState;
import com.yuvalshavit.effesvm.runtime.debugger.msg.Msg;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgAwaitRunStateChanged;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgGetModules;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgHello;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgIsSuspended;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgResume;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgStepIn;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgStepOut;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgStepOver;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgSuspend;

public class DebuggerGui {

  public static void connectTo(int port) throws IOException {
    DebugClient connection = DebugClient.start(port);
    CountDownLatch windowUp = new CountDownLatch(1);
    connection.communicate(new MsgHello(), ok -> {
      createDebugWindow(connection);
      windowUp.countDown();
    });
    try {
      windowUp.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  static void createDebugWindow(DebugClient connection) {
    new DebugWindow(connection).create();
  }

  private static class ResumeHandler {
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

  private static class DebugWindow {
    static final String connectionClosedMessage = "Connection closed";
    static final String suspendedMessage = "Suspended";
    static final String runningMessage = "Running";
    static final String resumeButtonText = "Resume";
    static final String suspendButtonText = "Suspend";

    private final DebugClient connection;
    private final DebuggerGuiState saveState = new DebuggerGuiState(new File("effesvm-debug-state.txt"));
    private FunctionsView functionsView;
    private FramesView framesView;


    public DebugWindow(DebugClient connection) {
      this.connection = connection;
    }

    public void create() {
      JPanel mainPanel = new JPanel();
      mainPanel.setLayout(new BorderLayout());

      ResumeHandler resumeHandler = new ResumeHandler(connection);
      framesView = new FramesView(connection);

      JLabel stateLabel = new JLabel("Remote state pending");
      JButton resumeButton = createResumeButton(stateLabel, resumeHandler);
      JPanel stepButtons = createStepButtons(stateLabel, resumeHandler);
      mainPanel.add(stepButtons, BorderLayout.NORTH);

      resumeHandler.enabledIffSuspended(framesView.getRootPane());
      resumeHandler.enabledIffSuspended(stepButtons);
      mainPanel.add(framesView.getRootPane(), BorderLayout.CENTER);
      connection.addCloseHandler(() -> {
        stateLabel.setText(connectionClosedMessage);
        stateLabel.setEnabled(false);
        resumeButton.setEnabled(false);
      });

      JPanel topPanel = new JPanel();
      topPanel.add(stateLabel);
      topPanel.add(resumeButton);

      JFrame frame = new JFrame("Debugger connected to " + connection.port());
      frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      frame.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosed(WindowEvent event) {
          try {
            connection.close();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          ConnectDialogue.create(connection.port());
        }
      });
      connection.addCloseHandler(resumeHandler::suspendGui);

      JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
      mainSplit.setPreferredSize(new Dimension(1200, 700));
      connection.communicate(new MsgGetModules(), resp -> {
        Map<EffesModule.Id, Map<EffesFunctionId, MsgGetModules.FunctionInfo>> functionsByModule = resp.getFunctions();
        functionsView = new FunctionsView(saveState, functionsByModule);
        framesView.setUpdateListener(functionsView::activate);
        functionsView.openConnection(connection);
        Container pane = functionsView.getRootContent();
        resumeHandler.enabledIffSuspended(pane);
        mainSplit.setLeftComponent(pane);
        mainSplit.setDividerLocation(0.5);
      });
      frame.getContentPane().add(mainSplit);

      JPanel content = new JPanel(new BorderLayout());
      content.add(topPanel, BorderLayout.NORTH);
      content.add(mainPanel, BorderLayout.CENTER);
      mainSplit.setRightComponent(content);
      frame.setLocationRelativeTo(null);
      frame.pack();
      frame.setVisible(true);

      resumeHandler.startWatching();
    }

    private JButton createResumeButton(JLabel stateLabel, ResumeHandler resumeHandler) {
      JButton resumeButton = new JButton(resumeButtonText);
      resumeButton.addActionListener(l -> {
        resumeHandler.suspendGui();
        switch (resumeButton.getText()) {
          case resumeButtonText:
            resumeButton.setText(suspendButtonText);
            stateLabel.setText("Resuming...");
            framesView.clearStackFrameInfo();
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
        framesView.updateStackFrameInfo();
      });
      return resumeButton;
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

  }

}
