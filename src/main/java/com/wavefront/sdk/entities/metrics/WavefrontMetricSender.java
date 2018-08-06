package com.wavefront.sdk.entities.metrics;

import java.io.IOException;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * WavefrontMetricSender interface that sends a metric to Wavefront
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public interface WavefrontMetricSender {

  /**
   * Sends the given metric to Wavefront
   *
   * @param name      The name of the metric. Spaces are replaced with '-' (dashes) and quotes will
   *                  be automatically escaped.
   * @param value     The value to be sent.
   * @param timestamp The timestamp in milliseconds since the epoch to be sent. If null then the
   *                  timestamp is assigned by Wavefront when data is received.
   * @param source    The source (or host) that's sending the metric. If null then assigned by
   *                  Wavefront.
   * @param tags      The tags associated with this metric.
   * @throws IOException if there was an error sending the metric.
   */
  void sendMetric(String name, double value, @Nullable Long timestamp, @Nullable String source,
                  @Nullable Map<String, String> tags) throws IOException;
}
