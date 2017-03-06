package com.yuvalshavit.effesvm.runtime;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.management.MBeanServer;
import javax.management.ObjectName;

public class DebugServer implements Consumer<EffesState> {

  private volatile BeanImpl bean;

  public void start(boolean suspend) {
    MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();
    bean = new BeanImpl(suspend);
    try {
      beanServer.registerMBean(bean, new ObjectName("effesvm:type=Debugger"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void accept(EffesState effesState) {
    BeanImpl bean = this.bean; // work with local ref
    if (bean != null) {
      try {
        bean.awaitIfSuspended();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
  }

  private class BeanImpl implements DebugServerMXBean {
    private final ConcurrentHashMap<String,Set<Integer>> breakpoints;
    private boolean isSuspended;

    public BeanImpl(boolean suspend) {
      isSuspended = suspend;
      this.breakpoints = new ConcurrentHashMap<>();
    }

    void awaitIfSuspended() throws InterruptedException {
      synchronized (this) {
        if (isSuspended) {
          System.err.println("Suspended. Awaiting release.");
          while (isSuspended) {
            wait();
          }
          System.err.println("Released");
        }
      }
    }

    @Override
    public boolean isSuspended() {
      synchronized (this) {
        return isSuspended;
      }
    }

    @Override
    public void setSuspended(boolean isSuspended) {
      synchronized (this) {
        boolean previouslySuspended = this.isSuspended;
        this.isSuspended = isSuspended;
        if (previouslySuspended && (!isSuspended)) {
          notifyAll();
        }
      }
    }

    @Override
    public List<String> getBreakpoints() {
      List<String> results = new ArrayList<>(breakpoints.size()); // size is a good guess
      for (Map.Entry<String,Set<Integer>> moduleBreakpoints : breakpoints.entrySet()) {
        String module = moduleBreakpoints.getKey();
        TreeSet<Integer> pointsForModule = new TreeSet<>(moduleBreakpoints.getValue());
        for (Integer line : pointsForModule) {
          results.add(String.format("%s #%s", module, line));
        }
      }
      return results;
    }

    @Override
    public void registerBreakpoint(String module, int line) {
      breakpoints.computeIfAbsent(module, k -> ConcurrentHashMap.newKeySet()).add(line);
    }

    @Override
    public boolean unregisterBreakpoint(String module, int line) {
      return breakpoints.getOrDefault(module, Collections.emptySet()).remove(line);
    }

    @Override
    public List<String> stackTrace() {
      return Arrays.asList("hello", "world");
    }
  }
}
