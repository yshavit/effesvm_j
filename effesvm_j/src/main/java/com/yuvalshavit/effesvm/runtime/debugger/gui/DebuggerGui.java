package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.WindowConstants;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.runtime.debugger.DebugClient;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerGuiState;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgGetModules;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgHello;

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

  private static class DebugWindow {

    private final DebugClient connection;
    private final DebuggerGuiState saveState = new DebuggerGuiState(new File("effesvm-debug-state.txt"));
    private FunctionsView functionsView;

    public DebugWindow(DebugClient connection) {
      this.connection = connection;
    }

    public void create() {
      ResumeHandler resumeHandler = new ResumeHandler(connection);
      FramesView framesView = new FramesView(connection);
      ControlsView controlsView = new ControlsView(connection, resumeHandler, framesView::updateStackFrameInfo, framesView::updateStackFrameInfo);
      Component stepButtons = controlsView.getStepButtons();
      resumeHandler.enabledIffSuspended(framesView.getRootPane());
      resumeHandler.enabledIffSuspended(stepButtons);
      connection.addCloseHandler(controlsView::showConnectionClosed);
      connection.addCloseHandler(resumeHandler::suspendGui);

      JSplitPane mainSplit = showMainWindow(framesView, controlsView, stepButtons);

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
      resumeHandler.startWatching();
    }

    private JSplitPane showMainWindow(FramesView framesView, ControlsView controlsView, Component stepButtons) {
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
      JPanel framesAndSteps = new JPanel();
      framesAndSteps.setLayout(new BorderLayout());
      framesAndSteps.add(stepButtons, BorderLayout.NORTH);
      framesAndSteps.add(framesView.getRootPane(), BorderLayout.CENTER);

      JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
      mainSplit.setPreferredSize(new Dimension(1200, 700));
      frame.getContentPane().add(mainSplit);

      JPanel framesAndControls = new JPanel(new BorderLayout());
      framesAndControls.add(controlsView.getResumeButtonPane(), BorderLayout.NORTH);
      framesAndControls.add(framesAndSteps, BorderLayout.CENTER);
      mainSplit.setRightComponent(framesAndControls);
      frame.setLocationRelativeTo(null);
      frame.pack();
      frame.setVisible(true);
      return mainSplit;
    }
  }
}
