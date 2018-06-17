package com.yuvalshavit.effesvm.util;

public class Average {
  private int total;
  private int count;

  public Average add(int value, int count) {
    this.total += value;
    this.count += count;
    return this;
  }

  public double get() {
    return ((double) total) / ((double) count);
  }

  public int total() {
    return total;
  }

  public int count() {
    return count;
  }
}
