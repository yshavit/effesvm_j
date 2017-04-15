package com.yuvalshavit.effesvm.runtime.debugger;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
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
  public static final int DEFAULT_PORT = 6667;

  private static final ThreadFactory daemonThreads = (r) -> {
    Thread t = new Thread(r);
    t.setDaemon(true);
    return t;
  };

  public static void createConnectDialogue(int initialPort) {
    if (initialPort <= 0) {
      initialPort = DEFAULT_PORT;
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
    private DefaultListModel<String> frameInfo;
    private OpsListWindow opsFrame;

    public DebugWindow(DebugConnection connection) {
      this.connection = connection;
    }

    public void create() {
      JFrame frame = new JFrame("Debugger connected to " + connection.port);
      frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      frame.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosed(WindowEvent event) {
          try {
            connection.close();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          createConnectDialogue(connection.port);
        }
      });
      JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
      mainSplit.setPreferredSize(new Dimension(1200, 700));

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

      JPanel stepButtons = new JPanel();
      stepButtons.add(stepButton("In ⇲", new MsgStepIn()));
      stepButtons.add(stepButton("Over ↷", new MsgStepOver()));
      stepButtons.add(stepButton("Out ⇱", new MsgStepOut()));
      mainPanel.add(stepButtons, BorderLayout.NORTH);

      frameInfo = new DefaultListModel<>();
      Pattern frameDividerPattern = Pattern.compile("\\[ *\\d+\\] *(\\* *)?\\[==");
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

      ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(daemonThreads);
      scheduler.scheduleWithFixedDelay(() -> connection.communicate(new MsgHello(), ok -> {}), 1500, 500, TimeUnit.MILLISECONDS);
      connection.addCloseHandler(() -> {
        onConnectionClosed(topPanel);
        scheduler.shutdownNow();
      });
      connection.communicate(new MsgIsSuspended(), isSuspended -> {
        if (!stateLabel.getText().equals(connectionClosedMessage)) {
          stateLabel.setText(isSuspended ? suspendedMessage : runningMessage);
          resumeButton.setText(isSuspended ? resumeButtonText : suspendButtonText);
          setEnabledRecursively(mainPanel, isSuspended);
          if (isSuspended) {
            updateStackFrameInfo();
          }
        }
      });
      connection.communicate(new MsgGetModules(), resp -> {
        Map<String,Map<String,List<String>>> functionsByModules = resp.functionsByModule();
        opsFrame = createOpsFrame(functionsByModules, pane -> {
          mainSplit.setLeftComponent(pane);
          mainSplit.setDividerLocation(0.5);
        });
      });

      frame.getContentPane().add(mainSplit);
      JPanel content = new JPanel(new BorderLayout());
      mainSplit.setRightComponent(content);
      content.add(topPanel, BorderLayout.NORTH);
      content.add(mainPanel, BorderLayout.CENTER);
      frame.setLocationRelativeTo(null);
      frame.pack();
      frame.setVisible(true);
    }

    private OpsListWindow createOpsFrame(Map<String,Map<String,List<String>>> functionsByModules, Consumer<Component> add) {
      Map<String,List<String>> functionNamesByModule = new HashMap<>();
      Map<Map.Entry<String,String>,List<String>> opsByFunction = new HashMap<>();
      Map<String,String> activeFunctionPerModule = new HashMap<>();

      functionsByModules.forEach((moduleId, functions) -> {
        List<String> functionNames = new ArrayList<>(functions.size());
        functionNamesByModule.put(moduleId, functionNames);
        functions.forEach((functionName, ops) -> {
          functionNames.add(functionName);
          Map.Entry<String,String> functionId = new AbstractMap.SimpleImmutableEntry<>(moduleId, functionName);
          opsByFunction.put(functionId, ops);
        });
        Collections.sort(functionNames);
        activeFunctionPerModule.put(moduleId, functionNames.get(0));
      });

      JComboBox<String> modulesChooserBox = new JComboBox<>(functionsByModules.keySet().toArray(new String[0]));
      DefaultComboBoxModel<String> functionChooserModel = new DefaultComboBoxModel<>();
      JComboBox<String> functionsComboBox = new JComboBox<>(functionChooserModel);
      DefaultListModel<String> activeOpsModel = new DefaultListModel<>();
      JList<String> activeOpsList = new JList<>(activeOpsModel);

      modulesChooserBox.addActionListener(action -> {
        String moduleName = (String) modulesChooserBox.getSelectedItem();
        functionChooserModel.removeAllElements();
        String activeFunction = activeFunctionPerModule.get(moduleName); // save this before the function box's listener overwrites it
        functionNamesByModule.getOrDefault(moduleName, Collections.emptyList()).forEach(functionChooserModel::addElement);
        functionChooserModel.setSelectedItem(activeFunction);
      });
      functionsComboBox.addActionListener(action -> {
        String moduleName = (String) modulesChooserBox.getSelectedItem();
        String functionName = (String) functionChooserModel.getSelectedItem();
        Map.Entry<String,String> functionId = new AbstractMap.SimpleImmutableEntry<>(moduleName, functionName);
        activeOpsModel.clear();
        opsByFunction.getOrDefault(functionId, Collections.singletonList("ERROR: no function " + functionId)).forEach(activeOpsModel::addElement);
        if (functionName != null) {
          activeFunctionPerModule.put(moduleName, functionName);
        }
      });

      Container rootContent = new JPanel();
      rootContent.setLayout(new BorderLayout());
      OpsListWindow window = new OpsListWindow() {
        @Override
        void activate(String moduleName, String functionName, int opIdx) {
          this.activeModule = moduleName;
          this.activeFunction = functionName;
          this.activeOpIdx = opIdx;
          activeFunctionPerModule.put(moduleName, functionName);
          modulesChooserBox.setSelectedItem(moduleName); // will also update the function, and page in the ops
          activeOpsList.setSelectedIndex(opIdx);
        }
      };

      JPanel selectorGroup = new JPanel();
      selectorGroup.add(modulesChooserBox);
      selectorGroup.add(functionsComboBox);
      rootContent.add(selectorGroup, BorderLayout.NORTH);

      JScrollPane opsScrollPane = new JScrollPane(activeOpsList);
      opsScrollPane.setPreferredSize(new Dimension(600, 700));
      activeOpsList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

      rootContent.add(opsScrollPane, BorderLayout.CENTER);
      Pattern lineNumberFinder = Pattern.compile("^#(\\d+) *");
      activeOpsList.setCellRenderer(new DefaultListCellRenderer() {
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
          String moduleName = (String) modulesChooserBox.getSelectedItem();
          String functionName = (String) functionChooserModel.getSelectedItem();
          // The following object has to match the one set in the lambda
          if (Objects.equals(moduleName, window.activeModule) && Objects.equals(functionName, window.activeFunction) && window.activeOpIdx == index) {
            fromSuper.setBackground(Color.LIGHT_GRAY);
          }
          return fromSuper;
        }
      });
      add.accept(rootContent);
      return window;
    }

    private abstract class OpsListWindow {
      String activeModule;
      String activeFunction;
      int activeOpIdx;

      abstract void activate(String moduleId, String functionName, int opIdx);
    }

    private JButton stepButton(String label, Msg.NoResponse message) {
      JButton stepOverButton = new JButton(label);
      stepOverButton.addActionListener(l -> {
        connection.communicate(message, ok -> {
          stateLabel.setText(suspendedMessage);
          setEnabledRecursively(mainPanel, false);
          connection.communicate(new MsgAwaitSuspension(), suspended -> {
            setEnabledRecursively(mainPanel, true);
            updateStackFrameInfo();
          });
        });
      });
      return stepOverButton;
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
        resumeButton.setText(resumeButtonText);
        setEnabledRecursively(mainPanel, true);
        updateStackFrameInfo();
      });
    }

    private void onResume() {
      resumeButton.setText(suspendButtonText);
      setEnabledRecursively(mainPanel, false);
      stateLabel.setText("Resuming...");
      frameInfo.clear();
      connection.communicate(new MsgResume(), ok -> stateLabel.setText(runningMessage));
    }

    private void updateStackFrameInfo() {
      connection.communicate(new MsgGetFrame(), frames -> {
        frameInfo.clear();
        String howMany = (frames.getStepsCompleted() == 1)
          ? "1 step completed"
          : (frames.getStepsCompleted() + " steps completed");
        frameInfo.addElement(howMany);
        frames.describeElements().forEach(frameInfo::addElement);
        opsFrame.activate(frames.getCurrentModuleId(), frames.getCurrentFunctionName(), frames.getOpIndex());
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
