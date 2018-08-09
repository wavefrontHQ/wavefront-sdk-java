package com.wavefront.sdk.direct_ingestion;


import com.wavefront.sdk.common.Constants;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.histograms.WavefrontHistogramSender;
import com.wavefront.sdk.entities.metrics.WavefrontMetricSender;
import com.wavefront.sdk.entities.tracing.SpanLog;
import com.wavefront.sdk.entities.tracing.WavefrontTracingSpanSender;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.ws.rs.core.Response;

import static com.wavefront.sdk.common.Utils.histogramToLineData;
import static com.wavefront.sdk.common.Utils.metricToLineData;
import static com.wavefront.sdk.common.Utils.tracingSpanToLineData;

/**
 * Wavefront direct ingestion client that sends data directly to Wavefront cluster via the direct ingestion API.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public class WavefrontDirectIngestionClient extends AbstractDirectConnectionHandler
    implements WavefrontMetricSender, WavefrontHistogramSender, WavefrontTracingSpanSender {

  private static final String DEFAULT_SOURCE = "wavefrontDirectSender";
  private static final Logger LOGGER = Logger.getLogger(
      WavefrontDirectIngestionClient.class.getCanonicalName());
  private static final int MAX_QUEUE_SIZE = 50000;
  private static final int BATCH_SIZE = 10000;

  private final LinkedBlockingQueue<String> metricsBuffer =
      new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
  private final LinkedBlockingQueue<String> histogramsBuffer =
      new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
  private final LinkedBlockingQueue<String> tracingSpansBuffer =
      new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);

  private final AtomicInteger failures = new AtomicInteger();

  /**
   * Creates a new client that connects directly to a given Wavefront service.
   *
   * @param server A Wavefront server URL of the form "https://clusterName.wavefront.com"
   * @param token A valid API token with direct ingestion permissions
   */
  public WavefrontDirectIngestionClient(String server, String token) {
    super(server, token);
  }

  @Override
  public void sendMetric(String name, double value, @Nullable Long timestamp,
                         @Nullable String source, @Nullable Map<String, String> tags)
      throws IOException {
    String point = metricToLineData(name, value, timestamp, source, tags, DEFAULT_SOURCE);
    if (!metricsBuffer.offer(point)) {
      LOGGER.log(Level.FINE, "Buffer full, dropping metric point: " + point);
    }
  }

  @Override
  public void sendDistribution(String name, List<Pair<Double, Integer>> centroids,
                               Set<HistogramGranularity> histogramGranularities,
                               @Nullable Long timestamp, @Nullable String source,
                               @Nullable Map<String, String> tags)
      throws IOException {
    String histograms = histogramToLineData(name, centroids, histogramGranularities, timestamp,
        source, tags, DEFAULT_SOURCE);
    if (!histogramsBuffer.offer(histograms)) {
      LOGGER.log(Level.FINE, "Buffer full, dropping histograms: " + histograms);
    }
  }

  @Override
  public void sendSpan(String name, long startMillis, long durationMicros,
                       @Nullable String source, UUID traceId, UUID spanId,
                       @Nullable List<UUID> parents, @Nullable List<UUID> followsFrom,
                       @Nullable List<Pair<String, String>> tags, @Nullable List<SpanLog> spanLogs)
      throws IOException {
    String span = tracingSpanToLineData(name, startMillis, durationMicros, source, traceId,
        spanId, parents, followsFrom, tags, spanLogs, DEFAULT_SOURCE);
    if (!tracingSpansBuffer.offer(span)) {
      LOGGER.log(Level.FINE, "Buffer full, dropping span: " + span);
    }
  }

  @Override
  protected void internalFlush() throws IOException {
    if (!isConnected()) {
      return;
    }
    internalFlush(metricsBuffer, Constants.WAVEFRONT_METRIC_FORMAT);
    internalFlush(histogramsBuffer, Constants.WAVEFRONT_HISTOGRAM_FORMAT);
    internalFlush(tracingSpansBuffer, Constants.WAVEFRONT_TRACING_SPAN_FORMAT);
  }

  private void internalFlush(LinkedBlockingQueue<String> buffer, String format)
      throws IOException {

    List<String> batch = getBatch(buffer);
    if (batch.isEmpty()) {
      return;
    }

    Response response = null;
    try (InputStream is = batchToStream(batch)) {
      response = report(format, is);
      if (response.getStatusInfo().getFamily() == Response.Status.Family.SERVER_ERROR ||
          response.getStatusInfo().getFamily() == Response.Status.Family.CLIENT_ERROR) {
        LOGGER.log(Level.FINE, "Error reporting points, respStatus=" + response.getStatus());
        try {
          buffer.addAll(batch);
        } catch (Exception ex) {
          // unlike offer(), addAll adds partially and throws an exception if buffer full
          LOGGER.log(Level.FINE, "Buffer full, dropping attempted points");
        }
      }
    } catch (IOException ex) {
      failures.incrementAndGet();
      throw ex;
    } finally {
      if (response != null) {
        response.close();
      }
    }
  }

  private List<String> getBatch(LinkedBlockingQueue<String> buffer) {
    int blockSize = Math.min(buffer.size(), BATCH_SIZE);
    List<String> points = new ArrayList<>(blockSize);
    buffer.drainTo(points, blockSize);
    return points;
  }

  private InputStream batchToStream(List<String> batch) {
    StringBuilder sb = new StringBuilder();
    for (String item : batch) {
      // every line item ends with \n
      sb.append(item);
    }
    return new ByteArrayInputStream(sb.toString().getBytes());
  }

  @Override
  public int getFailureCount() {
    return failures.get();
  }

  @Override
  public void run() {
    try {
      this.internalFlush();
    } catch (Throwable ex) {
      LOGGER.log(Level.FINE, "Unable to report to Wavefront cluster", ex);
    }
  }
}
