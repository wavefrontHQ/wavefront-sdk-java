package com.wavefront.sdk.common.metrics;

/**
 * A gauge used for metrics that are internal to Wavefront SDKs.
 *
 * @author Han Zhang (zhanghan@vmware.com).
 */
@FunctionalInterface
public interface WavefrontSdkGauge<T> extends WavefrontSdkMetric {
  /**
   * Gets the gauge's current value.
   *
   * @return The current value.
   */
  T getValue();
}
