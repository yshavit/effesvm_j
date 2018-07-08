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
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerEvents;
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

    private final DebuggerEvents debuggerEvents;
    private final DebuggerGuiState saveState = new DebuggerGuiState(new File("effesvm-debug-state.txt"));
    private final int connectionOriginalPort;
    private FunctionsView functionsView;

    public DebugWindow(DebugClient connection) {
      debuggerEvents = new DebuggerEvents(connection);
      connectionOriginalPort = connection.port();
    }

    public void create() {
      FramesView framesView = new FramesView(debuggerEvents);
      SourceModeCoordinator sourceModeCoordinator = new SourceModeCoordinator();
      ControlsView controlsView = new ControlsView(sourceModeCoordinator, debuggerEvents);
      Component stepButtons = controlsView.getStepButtons();

      JSplitPane mainSplit = showMainWindow(framesView, controlsView, stepButtons);

      debuggerEvents.communicate(new MsgGetModules(), resp -> {
        Map<EffesModule.Id, Map<EffesFunctionId, MsgGetModules.FunctionInfo>> functionsByModule = resp.getFunctions();
        functionsView = new FunctionsView(sourceModeCoordinator, saveState, functionsByModule, debuggerEvents);
        framesView.setUpdateListener(functionsView::activate);
        Container pane = functionsView.getRootContent();
        mainSplit.setLeftComponent(pane);
        mainSplit.setDividerLocation(0.5);

        debuggerEvents.sendInitialState();
      });
    }

    private JSplitPane showMainWindow(FramesView framesView, ControlsView controlsView, Component stepButtons) {
      JFrame frame = new JFrame("Debugger connected to " + connectionOriginalPort);
      frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      frame.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosed(WindowEvent event) {
          try {
            debuggerEvents.requestConnectionClose();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          ConnectDialogue.create(connectionOriginalPort);
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
      frame.pack();
      frame.setLocationRelativeTo(null);
      frame.setVisible(true);
      return mainSplit;
    }
  }
}
