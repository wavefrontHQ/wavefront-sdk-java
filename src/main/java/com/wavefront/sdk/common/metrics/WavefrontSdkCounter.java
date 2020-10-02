package com.wavefront.sdk.common.metrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A counter used for metrics that are internal to Wavefront SDKs.
 *
 * @author Han Zhang (zhanghan@vmware.com).
 */
public class WavefrontSdkCounter implements WavefrontSdkMetric {
  private final AtomicLong count;

  WavefrontSdkCounter() {
    count = new AtomicLong();
  }

  /**
   * Increments the counter by one.
   */
  public void inc() {
    inc(1);
  }

  /**
   * Increments the counter by the specified amount.
   *
   * @param n The amount to increment by.
   */
  public void inc(long n) {
    count.addAndGet(n);
  }

  /**
   * Decrements the counter by one.
   */
  public void dec() {
    dec(1);
  }

  /**
   * Decrements the counter by the specified amount.
   *
   * @param n The amount to decrement by.
   */
  public void dec(long n) {
    count.addAndGet(-n);
  }

  /**
   * Gets the counter's current value.
   *
   * @return The current value.
   */
  public long count() {
    return count.get();
  }

  /**
   * Resets the counter's value to 0.
   */
  public void clear() {
    count.set(0);
  }
}
