package com.wavefront.sdk.direct_ingestion;

import com.wavefront.sdk.common.BufferFlusher;
import com.wavefront.sdk.common.Constants;
import com.wavefront.sdk.common.NamedThreadFactory;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.histograms.WavefrontHistogramSender;
import com.wavefront.sdk.entities.metrics.WavefrontMetricSender;
import com.wavefront.sdk.entities.tracing.SpanLog;
import com.wavefront.sdk.entities.tracing.WavefrontTracingSpanSender;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
public class WavefrontDirectIngestionClient implements WavefrontMetricSender,
    WavefrontHistogramSender, WavefrontTracingSpanSender, BufferFlusher, Runnable, Closeable {

  private static final String DEFAULT_SOURCE = "wavefrontDirectSender";
  private static final Logger LOGGER = Logger.getLogger(
      WavefrontDirectIngestionClient.class.getCanonicalName());

  private final AtomicInteger failures = new AtomicInteger();
  private final int batchSize;
  private final LinkedBlockingQueue<String> metricsBuffer;
  private final LinkedBlockingQueue<String> histogramsBuffer;
  private final LinkedBlockingQueue<String> tracingSpansBuffer;
  private final DataIngesterAPI directService;
  private final ScheduledExecutorService scheduler;

  public static class Builder {
    // Required parameters
    private final String server;
    private final String token;

    // Optional parameters
    private int maxQueueSize = 50000;
    private int batchSize = 10000;
    private int flushIntervalSeconds = 1;

    /**
     * Create a new WavefrontDirectIngestionClient.Builder
     *
     * @param server A Wavefront server URL of the form "https://clusterName.wavefront.com"
     * @param token A valid API token with direct ingestion permissions
     */
    public Builder(String server, String token) {
      this.server = server;
      this.token = token;
    }

    /**
     * Set max queue size of in-memory buffer. Needs to be flushed if full.
     *
     * @param maxQueueSize Max queue size of in-memory buffer
     * @return WavefrontDirectIngestionClient.Builder instance
     */
    public Builder maxQueueSize(int maxQueueSize) {
      this.maxQueueSize = maxQueueSize;
      return this;
    }

    /**
     * Set batch size to be reported during every flush.
     *
     * @param batchSize Batch size to be reported during every flush.
     * @return WavefrontDirectIngestionClient.Builder instance
     */
    public Builder batchSize(int batchSize) {
      this.batchSize = batchSize;
      return this;
    }

    /**
     * Set interval at which you want to flush points to Wavefront cluster.
     *
     * @param flushIntervalSeconds Interval at which you want to flush points to Wavefront cluster
     * @return WavefrontDirectIngestionClient.Builder instance
     */
    public Builder flushIntervalSeconds(int flushIntervalSeconds) {
      this.flushIntervalSeconds = flushIntervalSeconds;
      return this;
    }

    /**
     * Creates a new client that connects directly to a given Wavefront service.
     *
     * @return wavefront direct-ingestion client
     */
    public WavefrontDirectIngestionClient build() {
      return new WavefrontDirectIngestionClient(this);
    }
  }

  private WavefrontDirectIngestionClient(Builder builder) {
    batchSize = builder.batchSize;
    metricsBuffer = new LinkedBlockingQueue<>(builder.maxQueueSize);
    histogramsBuffer = new LinkedBlockingQueue<>(builder.maxQueueSize);
    tracingSpansBuffer = new LinkedBlockingQueue<>(builder.maxQueueSize);
    directService = new DataIngesterService(builder.server, builder.token);
    scheduler = Executors.newScheduledThreadPool(1, new NamedThreadFactory(DEFAULT_SOURCE));
    scheduler.scheduleAtFixedRate(this, 1, builder.flushIntervalSeconds, TimeUnit.SECONDS);
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
  public void run() {
    try {
      this.flush();
    } catch (Throwable ex) {
      LOGGER.log(Level.FINE, "Unable to report to Wavefront cluster", ex);
    }
  }

  @Override
  public void flush() throws IOException {
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
      response = directService.report(format, is);
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
    int blockSize = Math.min(buffer.size(), batchSize);
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
  public synchronized void close() throws IOException {
    // Flush before closing
    flush();
    try {
      scheduler.shutdownNow();
    } catch (SecurityException ex) {
      LOGGER.log(Level.FINE, "shutdown error", ex);
    }
  }
}
