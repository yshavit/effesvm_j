package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.text.DecimalFormat;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;

import com.yuvalshavit.effesvm.runtime.debugger.DebugClient;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgHello;

public class ConnectDialogue {
  public static final int DEFAULT_PORT = 6667;

  private ConnectDialogue() {}

  public static void create(int initialPort) {
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
        DebuggerGui.createDebugWindow(debugConnection);
      });
    } catch (NumberFormatException | IOException e) {
      e.printStackTrace();
    }
  }
}
