package com.yuvalshavit.effesvm.runtime.debugger;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
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
  private static final int SCROLLTO_CONTEXT_BUFFER = 5;

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
      DebugClient debugConnection = DebugClient.start(Integer.parseInt(portString));
      debugConnection.communicate(new MsgHello(), r -> {
        connectionWindow.dispose();
        createDebugWindow(debugConnection);
      });
    } catch (NumberFormatException | IOException e) {
      e.printStackTrace();
    }
  }

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

  private static void createDebugWindow(DebugClient connection) {
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
    private DefaultListModel<String> frameInfo;

    private OpsListWindow opsFrame;

    public DebugWindow(DebugClient connection) {
      this.connection = connection;
    }

    public void create() {
      JPanel mainPanel = new JPanel();
      mainPanel.setLayout(new BorderLayout());

      ResumeHandler resumeHandler = new ResumeHandler(connection);

      JLabel stateLabel = new JLabel("Remote state pending");
      JButton resumeButton = createResumeButton(stateLabel, resumeHandler);
      JPanel stepButtons = createStepButtons(stateLabel, resumeHandler);
      mainPanel.add(stepButtons, BorderLayout.NORTH);

      frameInfo = new DefaultListModel<>();
      Container frameInfo = createFrameInfo();
      resumeHandler.enabledIffSuspended(frameInfo);
      resumeHandler.enabledIffSuspended(stepButtons);
      mainPanel.add(frameInfo, BorderLayout.CENTER);
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
          createConnectDialogue(connection.port());
        }
      });
      connection.addCloseHandler(resumeHandler::suspendGui);

      JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
      mainSplit.setPreferredSize(new Dimension(1200, 700));
      connection.communicate(new MsgGetModules(), resp -> {
        Map<String,Map<String,MsgGetModules.FunctionInfo>> functionsByModules = resp.functionsByModule();
        opsFrame = createOpsFrame(functionsByModules, pane -> {
          resumeHandler.enabledIffSuspended(pane);
          mainSplit.setLeftComponent(pane);
          mainSplit.setDividerLocation(0.5);
        });
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

    private Container createFrameInfo() {
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
      return frameInfoScrollPane;
    }

    private JButton createResumeButton(JLabel stateLabel, ResumeHandler resumeHandler) {
      JButton resumeButton = new JButton(resumeButtonText);
      resumeButton.addActionListener(l -> {
        resumeHandler.suspendGui();
        switch (resumeButton.getText()) {
          case resumeButtonText:
            resumeButton.setText(suspendButtonText);
            stateLabel.setText("Resuming...");
            frameInfo.clear();
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
        updateStackFrameInfo();
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

    private OpsListWindow createOpsFrame(Map<String,Map<String,MsgGetModules.FunctionInfo>> functionsByModules, Consumer<Component> add) {
      Map<String,List<String>> functionNamesByModule = new HashMap<>();
      Map<Map.Entry<String,String>,MsgGetModules.FunctionInfo> opsByFunction = new HashMap<>();
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
        opsByFunction
          .getOrDefault(functionId, new MsgGetModules.FunctionInfo(Collections.singletonList("ERROR: no function " + functionId), null))
          .opDescriptions()
          .forEach(activeOpsModel::addElement);
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
          Rectangle cellBounds = activeOpsList.getCellBounds(opIdx - SCROLLTO_CONTEXT_BUFFER, opIdx + SCROLLTO_CONTEXT_BUFFER);
          activeOpsList.scrollRectToVisible(cellBounds);
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
          String functionName = (String) functionsComboBox.getSelectedItem();
          if (Objects.equals(moduleName, window.activeModule) && Objects.equals(functionName, window.activeFunction) && window.activeOpIdx == index) {
            fromSuper.setBackground(Color.LIGHT_GRAY);
          }
          MsgGetModules.FunctionInfo functionInfo = opsByFunction.get(new AbstractMap.SimpleImmutableEntry<>(moduleName, functionName));
          if (functionInfo != null && functionInfo.breakpoints().get(index)) {
            fromSuper.setForeground(Color.RED);
          }
          return fromSuper;
        }
      });

      Set<MsgSetBreakpoints.Breakpoint> breakpoints = saveState.getBreakpoints();
      connection.communicate(new MsgSetBreakpoints(breakpoints, true), ok -> {
        for (MsgSetBreakpoints.Breakpoint breakpoint : breakpoints) {
          opsByFunction.get(new AbstractMap.SimpleImmutableEntry<>(breakpoint.getModuleId(), breakpoint.getFunctionId()))
            .breakpoints()
            .set(breakpoint.getOpIdx());
        }
        rootContent.repaint();
        activeOpsList.addMouseListener(new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
              String visibleModule = (String) modulesChooserBox.getSelectedItem();
              String visibleFunction = (String) functionsComboBox.getSelectedItem();
              int clickedItem = activeOpsList.locationToIndex(e.getPoint());
              BitSet breakpoints = opsByFunction.get(new AbstractMap.SimpleImmutableEntry<>(visibleModule, visibleFunction)).breakpoints();
              MsgSetBreakpoints.Breakpoint breakpoint = new MsgSetBreakpoints.Breakpoint(visibleModule, visibleFunction, clickedItem);
              boolean on = !breakpoints.get(clickedItem);
              saveState.setBreakpoint(breakpoint, on);
              MsgSetBreakpoints toggleMsg = new MsgSetBreakpoints(Collections.singleton(breakpoint), on);
              connection.communicate(toggleMsg, ok -> {
                breakpoints.flip(clickedItem);
                activeOpsList.repaint();
              });
            }
          }
        });
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

    private JButton stepButton(JLabel stateLabel, ResumeHandler resumeHandler, String label, Msg.NoResponse message) {
      JButton stepOverButton = new JButton(label);
      stepOverButton.addActionListener(l -> {
        connection.communicate(message, ok -> {
          stateLabel.setText(suspendedMessage);
          resumeHandler.suspendGui();
        });
      });
      resumeHandler.enabledIffSuspended(stepOverButton);
      return stepOverButton;
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

}
