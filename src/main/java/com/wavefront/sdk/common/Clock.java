package com.wavefront.sdk.common;

/**
 * Clock to track time.
 *
 * @author Vikram Raman (rvikram@vmware.com)
 */
public abstract class Clock {
  static long currTime = 0;

  static long nanoTime() {
    if (currTime == 0) {
      return System.nanoTime();
    }
    return currTime;
  }
}
