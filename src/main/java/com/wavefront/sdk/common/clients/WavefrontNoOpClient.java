package com.wavefront.sdk.common.clients;

import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.tracing.SpanLog;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Wavefront client that does not send any data to Wavefront.
 *
 * @author Tadaya Tsuyukubo
 * @version $Id: $Id
 */
public class WavefrontNoOpClient implements WavefrontSender {

  /** {@inheritDoc} */
  @Override
  public String getClientId() {
    return "NoOpClient";
  }

  /** {@inheritDoc} */
  @Override
  public void flush() throws IOException {
    // no-op
  }

  /** {@inheritDoc} */
  @Override
  public int getFailureCount() {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public void sendEvent(String name, long startMillis, long endMillis, String source,
                        Map<String, String> tags, Map<String, String> annotations) throws IOException {
    // no-op
  }

  /** {@inheritDoc} */
  @Override
  public void sendDistribution(String name, List<Pair<Double, Integer>> centroids,
                               Set<HistogramGranularity> histogramGranularities, Long timestamp, String source,
                               Map<String, String> tags) throws IOException {
    // no-op
  }

  /** {@inheritDoc} */
  @Override
  public void sendLog(String name, double value, Long timestamp, String source, Map<String, String> tags)
          throws IOException {
    // no-op
  }

  /** {@inheritDoc} */
  @Override
  public void sendMetric(String name, double value, Long timestamp, String source, Map<String, String> tags)
          throws IOException {
    // no-op
  }

  /** {@inheritDoc} */
  @Override
  public void sendFormattedMetric(String point) throws IOException {
    // no-op
  }

  /** {@inheritDoc} */
  @Override
  public void sendSpan(String name, long startMillis, long durationMillis, String source, UUID traceId,
                       UUID spanId, List<UUID> parents, List<UUID> followsFrom, List<Pair<String, String>> tags,
                       List<SpanLog> spanLogs) throws IOException {
    // no-op
  }

  /** {@inheritDoc} */
  @Override
  public void close() throws IOException {
    // no-op
  }

}
