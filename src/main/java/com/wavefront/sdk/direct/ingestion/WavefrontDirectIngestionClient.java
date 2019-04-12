package com.wavefront.sdk.direct.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.wavefront.sdk.common.Constants;
import com.wavefront.sdk.common.NamedThreadFactory;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.annotation.Nullable;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.tracing.SpanLog;

import java.io.ByteArrayInputStream;
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

import static com.wavefront.sdk.common.Utils.histogramToLineData;
import static com.wavefront.sdk.common.Utils.metricToLineData;
import static com.wavefront.sdk.common.Utils.spanLogsToJsonLine;
import static com.wavefront.sdk.common.Utils.tracingSpanToLineData;

/**
 * Wavefront direct ingestion client that sends data directly to Wavefront cluster via the direct ingestion API.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public class WavefrontDirectIngestionClient implements WavefrontSender, Runnable {

  private static final String DEFAULT_SOURCE = "wavefrontDirectSender";
  private static final Logger logger = Logger.getLogger(
      WavefrontDirectIngestionClient.class.getCanonicalName());

  private final AtomicInteger failures = new AtomicInteger();
  private final int batchSize;
  private final LinkedBlockingQueue<String> metricsBuffer;
  private final LinkedBlockingQueue<String> histogramsBuffer;
  private final LinkedBlockingQueue<String> tracingSpansBuffer;
  private final LinkedBlockingQueue<String> spanLogsBuffer;
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
     * @return {@code this}
     */
    public Builder maxQueueSize(int maxQueueSize) {
      this.maxQueueSize = maxQueueSize;
      return this;
    }

    /**
     * Set batch size to be reported during every flush.
     *
     * @param batchSize Batch size to be reported during every flush.
     * @return {@code this}
     */
    public Builder batchSize(int batchSize) {
      this.batchSize = batchSize;
      return this;
    }

    /**
     * Set interval at which you want to flush points to Wavefront cluster.
     *
     * @param flushIntervalSeconds Interval at which you want to flush points to Wavefront cluster
     * @return {@code this}
     */
    public Builder flushIntervalSeconds(int flushIntervalSeconds) {
      this.flushIntervalSeconds = flushIntervalSeconds;
      return this;
    }

    /**
     * Creates a new client that connects directly to a given Wavefront service.
     *
     * return {@link WavefrontDirectIngestionClient}
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
    spanLogsBuffer = new LinkedBlockingQueue<>(builder.maxQueueSize);
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
      logger.log(Level.WARNING, "Buffer full, dropping metric point: " + point);
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
      logger.log(Level.WARNING, "Buffer full, dropping histograms: " + histograms);
    }
  }

  @Override
  public void sendSpan(String name, long startMillis, long durationMillis,
                       @Nullable String source, UUID traceId, UUID spanId,
                       @Nullable List<UUID> parents, @Nullable List<UUID> followsFrom,
                       @Nullable List<Pair<String, String>> tags, @Nullable List<SpanLog> spanLogs)
      throws IOException {
    // first set the tag to indicate spanLogs are present, attempt to serialize the span
    boolean addSpanLogsTag = (spanLogs != null && !spanLogs.isEmpty());
    String span = tracingSpanToLineData(name, startMillis, durationMillis, source, traceId,
        spanId, parents, followsFrom, tags, addSpanLogsTag, DEFAULT_SOURCE);
    if (!tracingSpansBuffer.offer(span)) {
      logger.log(Level.WARNING, "Buffer full, dropping span: " + span);
    }
    // attempt span logs after span is sent.
    if (addSpanLogsTag) {
      sendSpanLogs(traceId, spanId, spanLogs);
    }
  }

  private void sendSpanLogs(UUID traceId, UUID spanId, List<SpanLog> spanLogs) {
    // attempt span logs
    try {
      String spanLogsJson = spanLogsToJsonLine(traceId, spanId, spanLogs);
      if (!spanLogsBuffer.offer(spanLogsJson)) {
        logger.log(Level.WARNING, "Buffer full, dropping spanLogs: " + spanLogsJson);
      }
    } catch (JsonProcessingException e) {
      logger.log(Level.WARNING, "unable to serialize span logs to json: traceId:" + traceId +
          " spanId:" + spanId + " spanLogs:" + spanLogs);
    }
  }

  @Override
  public void run() {
    try {
      this.flush();
    } catch (Throwable ex) {
      logger.log(Level.WARNING, "Unable to report to Wavefront cluster", ex);
    }
  }

  @Override
  public void flush() throws IOException {
    internalFlush(metricsBuffer, Constants.WAVEFRONT_METRIC_FORMAT);
    internalFlush(histogramsBuffer, Constants.WAVEFRONT_HISTOGRAM_FORMAT);
    internalFlush(tracingSpansBuffer, Constants.WAVEFRONT_TRACING_SPAN_FORMAT);
    internalFlush(spanLogsBuffer, Constants.WAVEFRONT_TRACING_SPAN_LOG_FORMAT);
  }

  private void internalFlush(LinkedBlockingQueue<String> buffer, String format)
      throws IOException {

    List<String> batch = getBatch(buffer);
    if (batch.isEmpty()) {
      return;
    }

    try (InputStream is = batchToStream(batch)) {
      int statusCode = directService.report(format, is);
      if (400 <= statusCode && statusCode <= 599) {
        logger.log(Level.WARNING, "Error reporting points, respStatus=" + statusCode);
        try {
          buffer.addAll(batch);
        } catch (Exception ex) {
          // unlike offer(), addAll adds partially and throws an exception if buffer full
          logger.log(Level.WARNING, "Buffer full, dropping attempted points");
        }
      }
    } catch (IOException ex) {
      failures.incrementAndGet();
      throw ex;
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
  public synchronized void close() {
    // Flush before closing
    try {
      flush();
    } catch (IOException e) {
      logger.log(Level.WARNING, "error flushing buffer", e);
    }
    try {
      scheduler.shutdownNow();
    } catch (SecurityException ex) {
      logger.log(Level.WARNING, "shutdown error", ex);
    }
  }
}
