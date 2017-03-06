package com.yuvalshavit.effesvm.runtime;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.ops.OpInfo;
import com.yuvalshavit.effesvm.ops.Operation;

public class DebugServer implements Runnable {

  private final EffesState effesState;
  private volatile BeanImpl bean;

  public DebugServer(EffesState effesState) {
    this.effesState = effesState;
  }

  public void start(boolean suspend) {
    bean = new BeanImpl();
    try {
      ManagementFactory.getPlatformMBeanServer().registerMBean(bean, bean.mainName);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    if (suspend) {
      bean.suspend();
      System.err.println("Suspended. Awaiting release.");
    }
  }

  @Override
  public void run() {
    BeanImpl bean = this.bean; // work with local ref
    if (bean != null) {
      bean.awaitIfSuspended();
      Operation op = effesState.pc().getOp();
      if (bean.atBreakpoint(op)) {
        bean.suspend();
        bean.awaitIfSuspended();
      }
    }
  }

  private class BeanImpl implements DebugServerMXBean {
    private final ObjectName mainName;
    private final ConcurrentHashMap<EffesModule.Id,Set<Integer>> breakpoints;
    private State state;

    public BeanImpl() {
      state = State.NORMAL;
      this.breakpoints = new ConcurrentHashMap<>();
      try {
        this.mainName = new ObjectName("effesvm:type=Debugger");
      } catch (MalformedObjectNameException e) {
        throw new RuntimeException(e);
      }
    }

    void awaitIfSuspended() {
      try {
        synchronized (this) {
          if (state != State.NORMAL) {
            if (state == State.STEPPING) {
              state = State.SUSPENDED;
            }
            while (state == State.SUSPENDED) {
              wait();
            }
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean isSuspended() {
      synchronized (this) {
        return state != State.NORMAL;
      }
    }

    @Override
    public void resume() {
      synchronized (this) {
        if (state == State.NORMAL) {
          throw new IllegalArgumentException("invalid state: " + state);
        } else {
          state = State.NORMAL;
        }
        notifyAll();
      }
    }

    @Override
    public void step() {
      synchronized (this) {
        if (state != State.SUSPENDED) {
          throw new IllegalArgumentException("invalid state: " + state);
        }
        state = State.STEPPING;
        notifyAll();
      }
    }

    @Override
    public void suspend() {
      synchronized (this) {
        if (state != State.NORMAL) {
          throw new IllegalArgumentException("invalid state: " + state);
        }
        this.state = State.SUSPENDED;
      }
    }

    @Override
    public List<String> getDebugState() {
      return effesState.toStringList();
    }

    @Override
    public List<String> getStackTrace() {
      return effesState.getStackTrace().stream()
        .filter(Objects::nonNull)
        .map(state -> String.format("%s (%s)%n", state.function().id(), state.function().opAt(state.pc()).info()))
        .collect(Collectors.toList());
    }

    boolean atBreakpoint(Operation op) {
      if (state != State.NORMAL) {
        return false;
      }
      OpInfo opInfo = op.info();
      Set<Integer> breakpoints = this.breakpoints.get(opInfo.module());
      return breakpoints != null && breakpoints.contains(opInfo.lineNumber());
    }

    @Override
    public List<String> getBreakpoints() {
      List<String> results = new ArrayList<>(breakpoints.size()); // size is a good guess
      for (Map.Entry<EffesModule.Id,Set<Integer>> moduleBreakpoints : breakpoints.entrySet()) {
        String module = moduleBreakpoints.getKey().toString();
        TreeSet<Integer> pointsForModule = new TreeSet<>(moduleBreakpoints.getValue());
        for (Integer line : pointsForModule) {
          results.add(String.format("%s #%s", module, line));
        }
      }
      return results;
    }

    @Override
    public void registerBreakpoint(String module, int line) {
      EffesModule.Id moduleId = EffesModule.Id.parse(module);
      breakpoints.computeIfAbsent(moduleId, k -> ConcurrentHashMap.newKeySet()).add(line);
    }

    @Override
    public boolean unregisterBreakpoint(String module, int line) {
      EffesModule.Id moduleId = EffesModule.Id.parse(module);
      return breakpoints.getOrDefault(moduleId, Collections.emptySet()).remove(line);
    }
  }

  private enum State {
    NORMAL,
    SUSPENDED,
    STEPPING,
  }
}
