package com.yuvalshavit.effesvm.runtime.debugger;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;

public class DebuggerGui {

  private static final ThreadFactory daemonThreads = (r) -> {
    Thread t = new Thread(r);
    t.setDaemon(true);
    return t;
  };

  public static void createConnectDialogue(int initialPort) {
    if (initialPort <= 0) {
      initialPort = 6166;
    }
    JFrame connectionWindow = new JFrame("EffesVM Debugger");
    connectionWindow.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    Container content = connectionWindow.getContentPane();

    content.add(new JLabel("Connect by port"), BorderLayout.WEST);

    JFormattedTextField portTextField = new JFormattedTextField(new DecimalFormat("#####"));
    portTextField.setColumns(5);
    portTextField.setText(Integer.toString(initialPort));
    ((PlainDocument) portTextField.getDocument()).setDocumentFilter(new DocumentFilter(){
      @Override
      public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
        if (isAllDigits(string)) {
          super.insertString(fb, offset, string, attr);
        }
      }

      @Override
      public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
        if (isAllDigits(text)) {
          super.replace(fb, offset, length, text, attrs);
        }
      }

      private boolean isAllDigits(String s) {
        return s.chars().allMatch((c) -> Character.isDigit((char)c));
      }
    });
    Object enterAction = new Object();
    portTextField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), enterAction);
    portTextField.getActionMap().put(enterAction, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        tryConnect(portTextField.getText(), connectionWindow);
      }
    });
    content.add(portTextField, BorderLayout.EAST);
    JButton connectButton = new JButton("Connection");
    connectButton.addActionListener(action -> tryConnect(portTextField.getText(), connectionWindow));
    content.add(connectButton, BorderLayout.SOUTH);

    connectionWindow.setLocationRelativeTo(null);
    connectionWindow.pack();
    connectionWindow.setVisible(true);
    // due to silliness, we have to wait a bit before we can select-all
    try {
      Thread.sleep(30);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    SwingUtilities.invokeLater(portTextField::selectAll);
  }

  private static void tryConnect(String portString, JFrame connectionWindow) {
    try {
      DebugConnection debugConnection = new DebugConnection(Integer.parseInt(portString));
      debugConnection.communicate(new MsgHello(), r -> {
        connectionWindow.dispose();
        createDebugWindow(debugConnection);
      });
    } catch (NumberFormatException | IOException e) {
      e.printStackTrace();
    }
  }

  public static void connectTo(int port) throws IOException {
    DebugConnection connection = new DebugConnection(port);
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
      frame.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosed(WindowEvent event) {
          if (opFrame != null) {
            opFrame.dispose();
          }
          try {
            connection.close();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          createConnectDialogue(connection.port);
        }
      });

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

      ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(daemonThreads);
      scheduler.scheduleWithFixedDelay(() -> connection.communicate(new MsgHello(), ok -> {}), 1500, 500, TimeUnit.MILLISECONDS);
      connection.addCloseHandler(() -> {
        onConnectionClosed(topPanel);
        scheduler.shutdownNow();
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
    private final ExecutorService communicationExecutor;
    private final Socket socket;

    public DebugConnection(int port) throws IOException {
      this.port = port;
      this.socket = new Socket(InetAddress.getLocalHost(), port);
      this.output = new ObjectOutputStream(socket.getOutputStream());
      this.input = new ObjectInputStream(socket.getInputStream());
      this.onClose = new LinkedBlockingDeque<>();
      communicationExecutor = Executors.newSingleThreadExecutor(daemonThreads);
    }

    void addCloseHandler(Runnable onClose) {
      this.onClose.add(onClose);
    }

    <R extends Serializable, M extends Msg<R>> void communicate(M message, Consumer<R> onSuccess, Consumer<Throwable> onFailure) {
      communicationExecutor.submit(() -> {
        try {
          output.writeObject(message);
          Object responseObj = input.readObject();
          Response<R> response = message.cast(responseObj);
          SwingUtilities.invokeLater(() -> response.handle(onSuccess, onFailure));
        } catch (EOFException | SocketException e) {
          try {
            close();
          } catch (IOException e1) {
            e1.printStackTrace();
          }
        } catch (Exception e) {
          Response<R> response = Response.forError(e);
          SwingUtilities.invokeLater(() -> response.handle(onSuccess, onFailure));
        }
      });
    }

    <R extends Serializable, M extends Msg<R>> void communicate(M message, Consumer<R> onSuccess) {
      communicate(message, onSuccess, Throwable::printStackTrace);
    }

    public void close() throws IOException {
      communicationExecutor.shutdownNow();
      for (Iterator<Runnable> iterator = onClose.iterator(); iterator.hasNext(); ) {
        Runnable runnable = iterator.next();
        try {
          runnable.run();
        } catch (Exception e2) {
          e2.printStackTrace();
        }
        iterator.remove();
      }
      socket.close();
    }
  }
}
