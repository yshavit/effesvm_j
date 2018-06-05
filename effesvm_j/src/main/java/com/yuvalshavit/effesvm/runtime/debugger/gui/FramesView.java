package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JScrollPane;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerEvents;
import com.yuvalshavit.effesvm.runtime.debugger.msg.MsgGetFrame;

class FramesView {
  private final DebuggerEvents debuggerEvents;
  private FrameUpdateListener updateListener;
  private DefaultListModel<String> frameInfo;
  private final Container rootPane;

  FramesView(DebuggerEvents debuggerEvents) {
    this.debuggerEvents = debuggerEvents;
    frameInfo = new DefaultListModel<>();
    rootPane = createFrameInfo(debuggerEvents);
    debuggerEvents.on(DebuggerEvents.Type.SUSPENDED, this::updateStackFrameInfo);
    debuggerEvents.on(DebuggerEvents.Type.CLOSED, frameInfo::clear);
  }

  void setUpdateListener(FrameUpdateListener listener) {
    if (updateListener != null) {
      throw new IllegalStateException("already set");
    }
    this.updateListener = listener;
  }

  Container getRootPane() {
    return rootPane;
  }

  private void updateStackFrameInfo() {
    debuggerEvents.communicate(new MsgGetFrame(), frames -> {
      frameInfo.clear();
      String howMany = (frames.getStepsCompleted() == 1)
        ? "1 step completed"
        : (frames.getStepsCompleted() + " steps completed");
      frameInfo.addElement(howMany);
      frames.describeElements().forEach(frameInfo::addElement);
      updateListener.handle(frames.getFunctionId(), frames.getOpIndex());
    });
  }

  private Container createFrameInfo(DebuggerEvents debuggerEvents) {
    JList<String> frameInfoList = new JList<>(frameInfo);
    frameInfoList.setCellRenderer(new FrameListCellRenderer());
    frameInfoList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    debuggerEvents.on(DebuggerEvents.Type.SUSPENDED, () -> frameInfoList.setEnabled(true));
    debuggerEvents.on(DebuggerEvents.Type.RESUMED, () -> frameInfoList.setEnabled(false));
    JScrollPane frameInfoScrollPane = new JScrollPane(frameInfoList);
    frameInfoScrollPane.setPreferredSize(new Dimension(600, 400));
    return frameInfoScrollPane;
  }

  interface FrameUpdateListener {
    void handle(EffesFunctionId functionId, int opIdx);
  }

  private static class FrameListCellRenderer extends DefaultListCellRenderer {
    static final Pattern frameDividerPattern = Pattern.compile("\\[ *\\d+] *(\\* *)?\\[==");

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
  }
}
