package com.wavefront.sdk.direct.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.wavefront.sdk.common.Constants;
import com.wavefront.sdk.common.NamedThreadFactory;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.annotation.Nullable;
import com.wavefront.sdk.common.metrics.WavefrontSdkCounter;
import com.wavefront.sdk.common.metrics.WavefrontSdkMetricsRegistry;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.tracing.SpanLog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.wavefront.sdk.common.Utils.histogramToLineData;
import static com.wavefront.sdk.common.Utils.metricToLineData;
import static com.wavefront.sdk.common.Utils.spanLogsToLineData;
import static com.wavefront.sdk.common.Utils.tracingSpanToLineData;

/**
 * Wavefront direct ingestion client that sends data directly to Wavefront cluster via the direct ingestion API.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public class WavefrontDirectIngestionClient implements WavefrontSender, Runnable {

  private static final Logger logger = Logger.getLogger(
      WavefrontDirectIngestionClient.class.getCanonicalName());

  /**
   * Source to use if entity source is null
   */
  private final String defaultSource;
  private final String clientId;

  private final int batchSize;
  private final LinkedBlockingQueue<String> metricsBuffer;
  private final LinkedBlockingQueue<String> histogramsBuffer;
  private final LinkedBlockingQueue<String> tracingSpansBuffer;
  private final LinkedBlockingQueue<String> spanLogsBuffer;
  private final DataIngesterAPI directService;
  private final ScheduledExecutorService scheduler;
  private final WavefrontSdkMetricsRegistry sdkMetricsRegistry;

  // Internal point metrics
  private final WavefrontSdkCounter pointsValid;
  private final WavefrontSdkCounter pointsInvalid;
  private final WavefrontSdkCounter pointsDropped;
  private final WavefrontSdkCounter pointReportErrors;

  // Internal histogram metrics
  private final WavefrontSdkCounter histogramsValid;
  private final WavefrontSdkCounter histogramsInvalid;
  private final WavefrontSdkCounter histogramsDropped;
  private final WavefrontSdkCounter histogramReportErrors;

  // Internal tracing span metrics
  private final WavefrontSdkCounter spansValid;
  private final WavefrontSdkCounter spansInvalid;
  private final WavefrontSdkCounter spansDropped;
  private final WavefrontSdkCounter spanReportErrors;

  // Internal span log metrics
  private final WavefrontSdkCounter spanLogsValid;
  private final WavefrontSdkCounter spanLogsInvalid;
  private final WavefrontSdkCounter spanLogsDropped;
  private final WavefrontSdkCounter spanLogReportErrors;

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
     * @param token  A valid API token with direct ingestion permissions
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
    String tempSource = "unknown";
    try {
      tempSource = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException ex) {
      logger.log(Level.WARNING,
          "Unable to resolve local host name. Source will default to 'unknown'", ex);
    }
    defaultSource = tempSource;

    batchSize = builder.batchSize;
    metricsBuffer = new LinkedBlockingQueue<>(builder.maxQueueSize);
    histogramsBuffer = new LinkedBlockingQueue<>(builder.maxQueueSize);
    tracingSpansBuffer = new LinkedBlockingQueue<>(builder.maxQueueSize);
    spanLogsBuffer = new LinkedBlockingQueue<>(builder.maxQueueSize);
    directService = new DataIngesterService(builder.server, builder.token);
    scheduler = Executors.newScheduledThreadPool(1, new NamedThreadFactory("wavefrontDirectSender"));
    scheduler.scheduleAtFixedRate(this, 1, builder.flushIntervalSeconds, TimeUnit.SECONDS);

    String processId = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
    sdkMetricsRegistry = new WavefrontSdkMetricsRegistry.Builder(this).
        prefix(Constants.SDK_METRIC_PREFIX + ".core.sender.direct").
        tag(Constants.PROCESS_TAG_KEY, processId).
        build();

    sdkMetricsRegistry.newGauge("points.queue.size", metricsBuffer::size);
    sdkMetricsRegistry.newGauge("points.queue.remaining_capacity",
        metricsBuffer::remainingCapacity);
    pointsValid = sdkMetricsRegistry.newCounter("points.valid");
    pointsInvalid = sdkMetricsRegistry.newCounter("points.invalid");
    pointsDropped = sdkMetricsRegistry.newCounter("points.dropped");
    pointReportErrors = sdkMetricsRegistry.newCounter("points.report.errors");

    sdkMetricsRegistry.newGauge("histograms.queue.size", histogramsBuffer::size);
    sdkMetricsRegistry.newGauge("histograms.queue.remaining_capacity",
        histogramsBuffer::remainingCapacity);
    histogramsValid = sdkMetricsRegistry.newCounter("histograms.valid");
    histogramsInvalid = sdkMetricsRegistry.newCounter("histograms.invalid");
    histogramsDropped = sdkMetricsRegistry.newCounter("histograms.dropped");
    histogramReportErrors = sdkMetricsRegistry.newCounter("histograms.report.errors");

    sdkMetricsRegistry.newGauge("spans.queue.size", tracingSpansBuffer::size);
    sdkMetricsRegistry.newGauge("spans.queue.remaining_capacity",
        tracingSpansBuffer::remainingCapacity);
    spansValid = sdkMetricsRegistry.newCounter("spans.valid");
    spansInvalid = sdkMetricsRegistry.newCounter("spans.invalid");
    spansDropped = sdkMetricsRegistry.newCounter("spans.dropped");
    spanReportErrors = sdkMetricsRegistry.newCounter("spans.report.errors");

    sdkMetricsRegistry.newGauge("span_logs.queue.size", spanLogsBuffer::size);
    sdkMetricsRegistry.newGauge("span_logs.queue.remaining_capacity",
        spanLogsBuffer::remainingCapacity);
    spanLogsValid = sdkMetricsRegistry.newCounter("span_logs.valid");
    spanLogsInvalid = sdkMetricsRegistry.newCounter("span_logs.invalid");
    spanLogsDropped = sdkMetricsRegistry.newCounter("span_logs.dropped");
    spanLogReportErrors = sdkMetricsRegistry.newCounter("span_logs.report.errors");
    this.clientId = builder.server;
  }

  @Override
  public String getClientId() {
    return clientId;
  }

  @Override
  public void sendMetric(String name, double value, @Nullable Long timestamp,
                         @Nullable String source, @Nullable Map<String, String> tags)
      throws IOException {
    String point;
    try {
      point = metricToLineData(name, value, timestamp, source, tags, defaultSource);
      pointsValid.inc();
    } catch (IllegalArgumentException e) {
      pointsInvalid.inc();
      throw e;
    }

    if (!metricsBuffer.offer(point)) {
      pointsDropped.inc();
      logger.log(Level.WARNING, "Buffer full, dropping metric point: " + point);
    }
  }

  @Override
  public void sendFormattedMetric(String point) throws IOException {
    if (point == null || "".equals(point.trim())) {
      pointsInvalid.inc();
      throw new IllegalArgumentException("point must be non-null and in WF data format");
    }
    pointsValid.inc();
    String finalPoint = point.endsWith("\n") ? point : point + "\n";

    if (!metricsBuffer.offer(finalPoint)) {
      pointsDropped.inc();
      logger.log(Level.WARNING, "Buffer full, dropping metric point: " + finalPoint);
    }
  }

  @Override
  public void sendDistribution(String name, List<Pair<Double, Integer>> centroids,
                               Set<HistogramGranularity> histogramGranularities,
                               @Nullable Long timestamp, @Nullable String source,
                               @Nullable Map<String, String> tags)
      throws IOException {
    String histograms;
    try {
      histograms = histogramToLineData(name, centroids, histogramGranularities, timestamp,
          source, tags, defaultSource);
      histogramsValid.inc();
    } catch (IllegalArgumentException e) {
      histogramsInvalid.inc();
      throw e;
    }

    if (!histogramsBuffer.offer(histograms)) {
      histogramsDropped.inc();
      logger.log(Level.WARNING, "Buffer full, dropping histograms: " + histograms);
    }
  }

  @Override
  public void sendSpan(String name, long startMillis, long durationMillis,
                       @Nullable String source, UUID traceId, UUID spanId,
                       @Nullable List<UUID> parents, @Nullable List<UUID> followsFrom,
                       @Nullable List<Pair<String, String>> tags, @Nullable List<SpanLog> spanLogs)
      throws IOException {
    String span;
    try {
      span = tracingSpanToLineData(name, startMillis, durationMillis, source, traceId,
          spanId, parents, followsFrom, tags, spanLogs, defaultSource);
      spansValid.inc();
    } catch (IllegalArgumentException e) {
      spansInvalid.inc();
      throw e;
    }

    if (tracingSpansBuffer.offer(span)) {
      // attempt span logs after span is sent.
      if (spanLogs != null && !spanLogs.isEmpty()) {
        sendSpanLogs(traceId, spanId, spanLogs);
      }
    } else {
      spansDropped.inc();
      if (spanLogs != null && !spanLogs.isEmpty()) {
        spanLogsDropped.inc();
      }
      logger.log(Level.WARNING, "Buffer full, dropping span: " + span);
    }
  }

  private void sendSpanLogs(UUID traceId, UUID spanId, List<SpanLog> spanLogs) {
    // attempt span logs
    try {
      String spanLogsJson = spanLogsToLineData(traceId, spanId, spanLogs);
      spanLogsValid.inc();
      if (!spanLogsBuffer.offer(spanLogsJson)) {
        spanLogsDropped.inc();
        logger.log(Level.WARNING, "Buffer full, dropping spanLogs: " + spanLogsJson);
      }
    } catch (JsonProcessingException e) {
      spanLogsInvalid.inc();
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
    internalFlush(metricsBuffer, Constants.WAVEFRONT_METRIC_FORMAT, "points",
        pointsDropped, pointReportErrors);
    internalFlush(histogramsBuffer, Constants.WAVEFRONT_HISTOGRAM_FORMAT, "histograms",
        histogramsDropped, histogramReportErrors);
    internalFlush(tracingSpansBuffer, Constants.WAVEFRONT_TRACING_SPAN_FORMAT, "spans",
        spansDropped, spanReportErrors);
    internalFlush(spanLogsBuffer, Constants.WAVEFRONT_SPAN_LOG_FORMAT, "span_logs",
        spanLogsDropped, spanLogReportErrors);
  }

  private void internalFlush(LinkedBlockingQueue<String> buffer, String format, String entityPrefix,
                             WavefrontSdkCounter dropped, WavefrontSdkCounter reportErrors)
      throws IOException {

    List<String> batch = getBatch(buffer);
    if (batch.isEmpty()) {
      return;
    }

    try (InputStream is = batchToStream(batch)) {
      int statusCode = directService.report(format, is);
      sdkMetricsRegistry.newCounter(entityPrefix + ".report." + statusCode).inc();
      if (400 <= statusCode && statusCode <= 599) {
        logger.log(Level.WARNING, "Error reporting points, respStatus=" + statusCode);
        int numAddedBackToBuffer = 0;
        for (String item : batch) {
          if (buffer.offer(item)) {
            numAddedBackToBuffer++;
          } else {
            dropped.inc(batch.size() - numAddedBackToBuffer);
            logger.log(Level.WARNING, "Buffer full, dropping attempted points");
            return;
          }
        }
      }
    } catch (IOException ex) {
      dropped.inc(batch.size());
      reportErrors.inc();
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
    return (int) (pointReportErrors.count() + histogramReportErrors.count() +
        spanReportErrors.count());
  }

  @Override
  public synchronized void close() {
    // Flush before closing
    try {
      flush();
    } catch (IOException e) {
      logger.log(Level.WARNING, "error flushing buffer", e);
    }

    sdkMetricsRegistry.close();

    try {
      scheduler.shutdownNow();
    } catch (SecurityException ex) {
      logger.log(Level.WARNING, "shutdown error", ex);
    }
  }
}
