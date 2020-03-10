package com.wavefront.sdk.entities.metrics;

import com.wavefront.sdk.common.annotation.Nullable;

import java.io.IOException;
import java.util.Map;

import static com.wavefront.sdk.common.Constants.DELTA_PREFIX;
import static com.wavefront.sdk.common.Constants.DELTA_PREFIX_2;

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

  /**
   * Similar to {@link #sendMetric(String, double, Long, String, Map)}, only the {@code point}
   * argument is expected to already be in Wavefront Data Format
   *
   * @param point a single metric, encoded in Wavefront Data Format
   * @throws IOException if there was an error sending the metric.
   */
  void sendFormattedMetric(String point) throws IOException;

  /**
   * Sends the given delta counter to Wavefront. The timestamp for the point on the client side is
   * null because the final timestamp of the delta counter is assigned when the point is
   * aggregated on the server side. Do not use this method to send older points (say around 5 min
   * old) as they will be aggregated on server with the current timestamp which yields in a wrong
   * final aggregated value.
   *
   * @param name      The name of the delta counter. Name will be prefixed by ∆ if it does
   *                  not start with that symbol already. Also, spaces are replaced with '-'
   *                  (dashes) and quotes will be automatically escaped.
   * @param value     The delta value to be sent. This will be aggregated on the Wavefront server
   *                  side.
   * @param source    The source (or host) that's sending the metric. If null then assigned by
   *                  Wavefront.
   * @param tags      The tags associated with this metric.
   * @throws IOException if there was an error sending the metric.
   */
  default void sendDeltaCounter(String name, double value, @Nullable String source,
                                @Nullable Map<String, String> tags) throws IOException {
    if (!name.startsWith(DELTA_PREFIX) && !name.startsWith(DELTA_PREFIX_2)) {
      name = DELTA_PREFIX + name;
    }
    if (value > 0) {
      sendMetric(name, value, null, source, tags);
    }
  }
}
