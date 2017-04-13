package com.yuvalshavit.effesvm.runtime.debugger;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.*;

public class DebuggerGui {
  public static void createConnectDialogue() {
    JFrame connectionWindow = new JFrame("EffesVM Debugger");
    connectionWindow.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    Container content = connectionWindow.getContentPane();

    content.add(new JLabel("Connect by port"), BorderLayout.WEST);
    JTextField portField = new JTextField(16);
    portField.addActionListener(action -> tryConnect(action.getActionCommand(), connectionWindow));
    content.add(portField, BorderLayout.EAST);
    JButton connectButton = new JButton("Connection");
    connectButton.addActionListener(action -> tryConnect(portField.getText(), connectionWindow));
    content.add(connectButton, BorderLayout.SOUTH);

    connectionWindow.setLocationRelativeTo(null);
    connectionWindow.pack();
    connectionWindow.setVisible(true);
  }

  private static void tryConnect(String portString, JFrame connectionWindow) {
    int portInt;
    try {
      portInt = Integer.parseInt(portString);
    } catch (NumberFormatException e) {
      return;
    }
    try {
      Socket socket = new Socket(InetAddress.getLocalHost(), portInt);
      DebugConnection debugConnection = new DebugConnection(portInt, socket.getInputStream(), socket.getOutputStream());
      debugConnection.communicate(new MsgHello(), r -> {
        connectionWindow.dispose();
        createDebugWindow(debugConnection);
      });
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    createConnectDialogue();
  }

  private static void createDebugWindow(DebugConnection connection) {
    new DebugWindow(connection).create();
  }

  private static void setEnabledRecursively(Component component, boolean enabled) {
    // http://stackoverflow.com/a/13920371/1076640
    component.setEnabled(enabled);
    if (component instanceof Container) {
      for (Component child : ((Container) component).getComponents()) {
        setEnabledRecursively(child, enabled);
      }
    }
  }

  private static class DebugWindow {
    static final String connectionClosedMessage = "Connection closed";
    static final String suspendedMessage = "Suspended";
    static final String runningMessage = "Running";
    static final String resumeButtonText = "Resume";
    static final String suspendButtonText = "Suspend";

    private final DebugConnection connection;

    private JPanel mainPanel;
    private JLabel stateLabel;
    private JButton resumeButton;
    private JButton stepButton;
    private DefaultListModel<String> frameInfo;
    private DefaultListModel<String> opInfo;
    private volatile String currentFunctionId;
    private volatile int currentOpIdx = -1;
    private JFrame opFrame;
    private JList<String> opInfoList;

    public DebugWindow(DebugConnection connection) {
      this.connection = connection;
    }

    public void create() {
      JFrame frame = new JFrame("Debugger connected to " + connection.port);
      frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

      mainPanel = new JPanel();
      mainPanel.setLayout(new BorderLayout());
      JPanel topPanel = new JPanel();
      stateLabel = new JLabel("Remote state pending");
      topPanel.add(stateLabel);
      resumeButton = new JButton(resumeButtonText);
      topPanel.add(resumeButton);
      resumeButton.addActionListener(l -> {
        switch (resumeButton.getText()) {
          case resumeButtonText:
            onResume();
            break;
          case suspendButtonText:
            onSuspend();
            break;
        }
      });
      stepButton = new JButton("Step");
      stepButton.addActionListener(l -> connection.communicate(new MsgStep(), state -> updateStackFrameInfo()));
      mainPanel.add(stepButton, BorderLayout.NORTH);

      frameInfo = new DefaultListModel<>();
      Pattern frameDividerPattern = Pattern.compile("\\[\\d+\\] *(\\* *)?\\[==");
      JList<String> frameInfoList = new JList<>(frameInfo);
      frameInfoList.setCellRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          Component fromSuper = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          if (index <= 1) {
            fromSuper.setBackground(Color.LIGHT_GRAY);
            fromSuper.setForeground(Color.BLUE.darker());
            fromSuper.setFont(fromSuper.getFont().deriveFont(Font.BOLD));
            if (fromSuper instanceof JComponent) {
              JComponent superJComponent = (JComponent) fromSuper;
              superJComponent.setBorder(BorderFactory.createEtchedBorder());
            }
          } else if (value instanceof String) {
            String text = (String) value;
            Matcher frameDividerMatch = frameDividerPattern.matcher(text);
            if (frameDividerMatch.find()) {
              fromSuper.setBackground(Color.LIGHT_GRAY);
              if (frameDividerMatch.group(1) != null) {
                fromSuper.setFont(fromSuper.getFont().deriveFont(Font.BOLD));
              }
              if (fromSuper instanceof JComponent) {
                JComponent superJComponent = (JComponent) fromSuper;
                superJComponent.setBorder(BorderFactory.createEtchedBorder());
              }
            }
          }
          return fromSuper;
        }
      });
      frameInfoList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
      frameInfoList.setEnabled(false);
      JScrollPane frameInfoScrollPane = new JScrollPane(frameInfoList);
      frameInfoScrollPane.setPreferredSize(new Dimension(600, 400));
      mainPanel.add(frameInfoScrollPane, BorderLayout.CENTER);

      opFrame = new JFrame("ops");
      opInfo = new DefaultListModel<>();
      opInfoList = new JList<>(opInfo);
      Pattern lineNumberFinder = Pattern.compile("^#(\\d+) *");
      opInfoList.setCellRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          if (value instanceof String) {
            String valueStr = (String) value;
            Matcher lineNumberMatcher = lineNumberFinder.matcher(valueStr);
            if (lineNumberMatcher.find()) {
              StringBuilder sb = new StringBuilder(valueStr.length() + 10); // +10 is more than enough
              sb.append(index).append('(').append(lineNumberMatcher.group(1)).append(") ");
              sb.append(valueStr, lineNumberMatcher.end(), valueStr.length());
              value = sb.toString();
            }
          }
          Component fromSuper = super.getListCellRendererComponent(list, value, index, false, cellHasFocus);
          if (index == currentOpIdx) {
            fromSuper.setBackground(Color.LIGHT_GRAY);
          }
          return fromSuper;
        }
      });
      opInfoList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
      JScrollPane opInfoScrollPane = new JScrollPane(opInfoList);
      opInfoScrollPane.setPreferredSize(new Dimension(400, 600));
      opFrame.getContentPane().add(opInfoScrollPane);
      opFrame.pack();
      opFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

      // TODO: This causes problems with other messages getting interleaved. Need to single-thread the communication, or something.
//      ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
//      scheduler.scheduleWithFixedDelay(() -> connection.communicate(new MsgHello(), ok -> {}), 1500, 500, TimeUnit.MILLISECONDS);
      connection.addCloseHandler(() -> {
        onConnectionClosed(topPanel);
//        scheduler.shutdownNow();
      });
      connection.communicate(new MsgIsSuspended(), isSuspended -> {
        if (!stateLabel.getText().equals(connectionClosedMessage)) {
          stateLabel.setText(isSuspended ? suspendedMessage : runningMessage);
          setEnabledRecursively(mainPanel, isSuspended);
          if (isSuspended) {
            updateStackFrameInfo();
          }
        }
      });

      Container content = frame.getContentPane();
      content.add(topPanel, BorderLayout.NORTH);
      content.add(mainPanel, BorderLayout.CENTER);
      frame.setLocationRelativeTo(null);
      frame.pack();
      frame.setVisible(true);
    }

    private void onConnectionClosed(JPanel topPanel) {
      stateLabel.setText(connectionClosedMessage);
      setEnabledRecursively(mainPanel, false);
      setEnabledRecursively(topPanel, false);
    }

    private void onSuspend() {
      stateLabel.setText("Suspending...");
      connection.communicate(new MsgSuspend(), ok -> {
        stateLabel.setText(suspendButtonText);
        setEnabledRecursively(mainPanel, true);
      });
      opFrame.dispose();
    }

    private void onResume() {
      resumeButton.setText(suspendButtonText);
      setEnabledRecursively(mainPanel, false);
      stateLabel.setText("Resuming...");
      frameInfo.clear();
      connection.communicate(new MsgResume(), ok -> stateLabel.setText(runningMessage));
      connection.communicate(new MsgAwaitSuspension(), ok -> {
        resumeButton.setText(suspendButtonText);
        setEnabledRecursively(mainPanel, true);
      });
    }

    private void updateStackFrameInfo() {
      connection.communicate(new MsgGetFrame(currentFunctionId), frames -> {
        frameInfo.clear();
        String howMany = (frames.getStepsCompleted() == 1)
          ? "1 step completed"
          : (frames.getStepsCompleted() + " steps completed");
        frameInfo.addElement(howMany);
        frames.describeElements().forEach(frameInfo::addElement);
        currentFunctionId = frames.getCurrentFunctionId();
        currentOpIdx = frames.getOpIndex();
        opInfoList.setSelectedIndex(currentOpIdx);
        if (frames.getFunctionOps() != null) {
          opFrame.setTitle(frames.getCurrentFunctionId());
          opInfo.clear();
          frames.getFunctionOps().forEach(opInfo::addElement);
          opFrame.setVisible(true);
        }
        opFrame.repaint();
      });
    }
  }

  private static class DebugConnection {
    final int port;
    final ObjectInputStream input;
    final ObjectOutputStream output;
    final BlockingDeque<Runnable> onClose;

    public DebugConnection(int port, InputStream input, OutputStream output) throws IOException {
      this.port = port;
      this.output = new ObjectOutputStream(output);
      this.input = new ObjectInputStream(input);
      onClose = new LinkedBlockingDeque<>();
    }

    void addCloseHandler(Runnable onClose) {
      this.onClose.add(onClose);
    }

    <R extends Serializable, M extends Msg<R>> void communicate(M message, Consumer<R> onSuccess, Consumer<Throwable> onFailure) {
      SwingWorker<Response<R>,Void> worker = new SwingWorker<Response<R>,Void>() {
        @Override
        protected Response<R> doInBackground() throws Exception {
          try {
            output.writeObject(message);
            Object responseObj = input.readObject();
            return message.cast(responseObj);
          } catch (EOFException | SocketException e) {
            for (Iterator<Runnable> iterator = onClose.iterator(); iterator.hasNext(); ) {
              Runnable runnable = iterator.next();
              try {
                runnable.run();
              } catch (Exception e2) {
                e2.printStackTrace();
              }
              iterator.remove();
            }
            return null;
          } catch (Exception e) {
            return Response.forError(e);
          }
        }

        @Override
        protected void done() {
          Response<R> response;
          try {
            response = get();
          } catch (Exception e) {
            onFailure.accept(e);
            return;
          }
          response.handle(onSuccess, onFailure);
        }
      };
      worker.execute();
    }

    <R extends Serializable, M extends Msg<R>> void communicate(M message, Consumer<R> onSuccess) {
      communicate(message, onSuccess, Throwable::printStackTrace);
    }
  }
}
