package com.wavefront.sdk.proxy;

import com.wavefront.sdk.common.*;
import com.wavefront.sdk.common.annotation.Nullable;
import com.wavefront.sdk.common.metrics.WavefrontSdkCounter;
import com.wavefront.sdk.common.metrics.WavefrontSdkMetricsRegistry;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.tracing.SpanLog;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.SocketFactory;

import static com.wavefront.sdk.common.Utils.histogramToLineData;
import static com.wavefront.sdk.common.Utils.metricToLineData;
import static com.wavefront.sdk.common.Utils.tracingSpanToLineData;

/**
 * WavefrontProxyClient that sends data directly via TCP to the Wavefront Proxy Agent.
 * User should probably attempt to reconnect when exceptions are thrown from any methods.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class WavefrontProxyClient implements WavefrontSender, Runnable {

  private static final Logger logger = Logger.getLogger(
      WavefrontProxyClient.class.getCanonicalName());

  @Nullable
  private final ProxyConnectionHandler metricsProxyConnectionHandler;

  @Nullable
  private final ProxyConnectionHandler histogramProxyConnectionHandler;

  @Nullable
  private final ProxyConnectionHandler tracingProxyConnectionHandler;

  /**
   * Source to use if entity source is null
   */
  private final String defaultSource;

  private final ScheduledExecutorService scheduler;
  private final WavefrontSdkMetricsRegistry sdkMetricsRegistry;

  // Internal point metrics
  private final WavefrontSdkCounter pointsReceived;
  private final WavefrontSdkCounter pointsDropped;

  // Internal histogram metrics
  private final WavefrontSdkCounter histogramsReceived;
  private final WavefrontSdkCounter histogramsDropped;

  // Internal tracing span metrics
  private final WavefrontSdkCounter spansReceived;
  private final WavefrontSdkCounter spansDropped;

  private final WavefrontSdkCounter reportErrors;

  public static class Builder {
    // Required parameters
    private final String proxyHostName;

    // Optional parameters
    private Integer metricsPort;
    private Integer distributionPort;
    private Integer tracingPort;
    private SocketFactory socketFactory = SocketFactory.getDefault();
    private int flushIntervalSeconds = 5;

    /**
     * WavefrontProxyClient.Builder
     *
     * @param proxyHostName     Hostname of the Wavefront proxy
     */
    public Builder(String proxyHostName) {
      this.proxyHostName = proxyHostName;
    }

    /**
     * Invoke this method to enable sending metrics to Wavefront cluster via proxy
     *
     * @param metricsPort       Metrics Port on which the Wavefront proxy is listening on
     * @return {@code this}
     */
    public Builder metricsPort(int metricsPort) {
      this.metricsPort = metricsPort;
      return this;
    }

    /**
     * Invoke this method to enable sending distribution to Wavefront cluster via proxy
     *
     * @param distributionPort   Distribution Port on which the Wavefront proxy is listening on
     * @return {@code this}
     */
    public Builder distributionPort(int distributionPort) {
      this.distributionPort = distributionPort;
      return this;
    }

    /**
     * Invoke this method to enable sending tracing spans to Wavefront cluster via proxy
     *
     * @param tracingPort        Tracing Port on which the Wavefront proxy is listening on
     * @return {@code this}
     */
    public Builder tracingPort(int tracingPort) {
      this.tracingPort = tracingPort;
      return this;
    }

    /**
     * Set an explicit SocketFactory
     *
     * @param socketFactory       SocketFactory
     * @return {@code this}
     */
    public Builder socketFactory(SocketFactory socketFactory) {
      this.socketFactory = socketFactory;
      return this;
    }

    /**
     * Set interval at which you want to flush points to Wavefront proxy
     *
     * @param flushIntervalSeconds  Interval at which you want to flush points to Wavefront proxy
     * @return {@code this}
     */
    public Builder flushIntervalSeconds(int flushIntervalSeconds) {
      this.flushIntervalSeconds = flushIntervalSeconds;
      return this;
    }

    /**
     * Builds WavefrontProxyClient instance
     *
     * @return {@link WavefrontProxyClient}
     * @throws UnknownHostException
     */
    public WavefrontProxyClient build() {
      return new WavefrontProxyClient(this);
    }
  }

  private WavefrontProxyClient(Builder builder) {
    String tempSource = "unknown";
    try {
      tempSource = InetAddress.getLocalHost().getHostName();
    }
    catch (UnknownHostException ex) {
      logger.log(Level.WARNING,
              "Unable to resolve local host name. Source will default to 'unknown'", ex);
    }
    defaultSource = tempSource;

    if (builder.metricsPort == null) {
      metricsProxyConnectionHandler = null;
    } else {
      metricsProxyConnectionHandler = new ProxyConnectionHandler(
          new InetSocketAddress(builder.proxyHostName, builder.metricsPort),
          builder.socketFactory);
    }

    if (builder.distributionPort == null) {
      histogramProxyConnectionHandler = null;
    } else {
      histogramProxyConnectionHandler = new ProxyConnectionHandler(
          new InetSocketAddress(builder.proxyHostName, builder.distributionPort),
          builder.socketFactory);
    }

    if (builder.tracingPort == null) {
      tracingProxyConnectionHandler = null;
    } else {
      tracingProxyConnectionHandler = new ProxyConnectionHandler(
          new InetSocketAddress(builder.proxyHostName, builder.tracingPort),
          builder.socketFactory);
    }

    scheduler = Executors.newScheduledThreadPool(1,
        new NamedThreadFactory("wavefrontProxySender"));
    // flush every 5 seconds
    scheduler.scheduleAtFixedRate(this, 1, builder.flushIntervalSeconds, TimeUnit.SECONDS);

    sdkMetricsRegistry = new WavefrontSdkMetricsRegistry.Builder(this).
        prefix(Constants.SDK_METRIC_PREFIX + ".core.sender.proxy").
        build();

    pointsReceived = sdkMetricsRegistry.newCounter("points.received");
    pointsDropped = sdkMetricsRegistry.newCounter("points.dropped");

    histogramsReceived = sdkMetricsRegistry.newCounter("histograms.received");
    histogramsDropped = sdkMetricsRegistry.newCounter("histograms.dropped");

    spansReceived = sdkMetricsRegistry.newCounter("spans.received");
    spansDropped = sdkMetricsRegistry.newCounter("spans.dropped");

    reportErrors = sdkMetricsRegistry.newCounter("errors");
  }

  private boolean setupConnection() throws IOException {
    if (metricsProxyConnectionHandler == null) {
      logger.warning("Can't send data to Wavefront. " +
              "Please configure metrics port for Wavefront proxy");
      return false;
    }

    if (!metricsProxyConnectionHandler.isConnected()) {
      try {
        metricsProxyConnectionHandler.connect();
      } catch (IllegalStateException ex) {
        // already connected.
      }
    }
    return true;
  }

  @Override
  public void sendMetric(String name, double value, @Nullable Long timestamp,
                         @Nullable String source, @Nullable Map<String, String> tags)
      throws IOException {
    if (!setupConnection()) {
      return;
    }

    try {
      String lineData = metricToLineData(name, value, timestamp, source, tags, defaultSource);
      pointsReceived.inc();
      try {
        metricsProxyConnectionHandler.sendData(lineData);
      } catch (Exception e) {
        throw new IOException(e);
      }
    } catch (IOException e) {
      pointsDropped.inc();
      reportErrors.inc();
      metricsProxyConnectionHandler.incrementFailureCount();
      throw e;
    }
  }

  @Override
  public void sendFormattedMetric(String point) throws IOException {
    if (!setupConnection()) {
      return;
    }

    try {
      if (point == null || "".equals(point.trim())) {
        throw new IllegalArgumentException("point must be non-null and in WF data format");
      }
      pointsReceived.inc();
      String finalPoint = point.endsWith("\n") ? point : point + "\n";
      try {
        metricsProxyConnectionHandler.sendData(finalPoint);
      } catch (Exception e) {
        throw new IOException(e);
      }
    } catch (IOException e) {
      pointsDropped.inc();
      reportErrors.inc();
      metricsProxyConnectionHandler.incrementFailureCount();
      throw e;
    }
  }

  @Override
  public void sendDistribution(String name, List<Pair<Double, Integer>> centroids,
                               Set<HistogramGranularity> histogramGranularities,
                               @Nullable Long timestamp, @Nullable String source,
                               @Nullable Map<String, String> tags)
      throws IOException {
    if (histogramProxyConnectionHandler == null) {
      logger.warning("Can't send data to Wavefront. " +
              "Please configure histogram distribution port for Wavefront proxy");
      return;
    }

    if (!histogramProxyConnectionHandler.isConnected()) {
      try {
        histogramProxyConnectionHandler.connect();
      } catch (IllegalStateException ex) {
        // already connected.
      }
    }

    try {
      String lineData = histogramToLineData(name, centroids, histogramGranularities, timestamp,
          source, tags, defaultSource);
      histogramsReceived.inc();
      try {
        histogramProxyConnectionHandler.sendData(lineData);
      } catch (Exception e) {
        throw new IOException(e);
      }
    } catch (IOException e) {
      histogramsDropped.inc();
      reportErrors.inc();
      histogramProxyConnectionHandler.incrementFailureCount();
      throw e;
    }
  }

  @Override
  public void sendSpan(String name, long startMillis, long durationMillis,
                       @Nullable String source, UUID traceId, UUID spanId,
                       @Nullable List<UUID> parents, @Nullable List<UUID> followsFrom,
                       @Nullable List<Pair<String, String>> tags, @Nullable List<SpanLog> spanLogs)
      throws IOException {
    if (tracingProxyConnectionHandler == null) {
      logger.warning("Can't send data to Wavefront. " +
              "Please configure tracing port for Wavefront proxy");
      return;
    }

    if (!tracingProxyConnectionHandler.isConnected()) {
      try {
        tracingProxyConnectionHandler.connect();
      } catch (IllegalStateException ex) {
        // already connected.
      }
    }

    try {
      String lineData = tracingSpanToLineData(name, startMillis, durationMillis, source, traceId,
          spanId, parents, followsFrom, tags, spanLogs, defaultSource);
      spansReceived.inc();
      try {
        tracingProxyConnectionHandler.sendData(lineData);
      } catch (Exception e) {
        throw new IOException(e);
      }
    } catch (IOException e) {
      spansDropped.inc();
      reportErrors.inc();
      tracingProxyConnectionHandler.incrementFailureCount();
      throw e;
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
    if (metricsProxyConnectionHandler != null) {
      metricsProxyConnectionHandler.flush();
    }

    if (histogramProxyConnectionHandler != null) {
      histogramProxyConnectionHandler.flush();
    }

    if (tracingProxyConnectionHandler != null) {
      tracingProxyConnectionHandler.flush();
    }
  }

  @Override
  public int getFailureCount() {
    int failureCount = 0;
    if (metricsProxyConnectionHandler != null) {
      failureCount += metricsProxyConnectionHandler.getFailureCount();
    }

    if (histogramProxyConnectionHandler != null) {
      failureCount += histogramProxyConnectionHandler.getFailureCount();
    }

    if (tracingProxyConnectionHandler != null) {
      failureCount += tracingProxyConnectionHandler.getFailureCount();
    }
    return failureCount;
  }

  @Override
  public void close() {
    sdkMetricsRegistry.close();

    // Flush before closing
    try {
      flush();
    } catch (IOException e) {
      logger.log(Level.WARNING, "error flushing buffer", e);
    }

    try {
      scheduler.shutdownNow();
    } catch (SecurityException ex) {
      logger.log(Level.FINE, "shutdown error", ex);
    }

    if (metricsProxyConnectionHandler != null) {
      try {
        metricsProxyConnectionHandler.close();
      } catch (IOException e) {
        logger.log(Level.WARNING, "error closing metricsProxyConnectionHandler", e);
      }
    }

    if (histogramProxyConnectionHandler != null) {
      try {
        histogramProxyConnectionHandler.close();
      } catch (IOException e) {
        logger.log(Level.WARNING, "error closing histogramProxyConnectionHandler", e);
      }
    }

    if (tracingProxyConnectionHandler != null) {
      try {
        tracingProxyConnectionHandler.close();
      } catch (IOException e) {
        logger.log(Level.WARNING, "error closing tracingProxyConnectionHandler", e);
      }
    }
  }
}
