package com.wavefront.sdk.proxy;

import com.wavefront.sdk.common.ConnectionHandler;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.histograms.WavefrontHistogramSender;
import com.wavefront.sdk.entities.metrics.WavefrontMetricSender;
import com.wavefront.sdk.entities.tracing.SpanLog;
import com.wavefront.sdk.entities.tracing.WavefrontTracingSpanSender;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;
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
public class WavefrontProxyClient implements WavefrontMetricSender, WavefrontHistogramSender,
    WavefrontTracingSpanSender, ConnectionHandler {

  @Nullable
  private final ProxyConnectionHandler metricsProxyConnectionHandler;

  @Nullable
  private final ProxyConnectionHandler histogramProxyConnectionHandler;

  @Nullable
  private final ProxyConnectionHandler tracingProxyConnectionHandler;

  /**
   * Source to use if entity source is null
   */
  private final String defaultSource = InetAddress.getLocalHost().getHostName();

  public static class Builder {
    // Required parameters
    private final String proxyHostName;

    // Optional parameters
    private Integer metricsPort;
    private Integer distributionPort;
    private Integer tracingPort;
    private SocketFactory socketFactory = SocketFactory.getDefault();

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
     * @return WavefrontProxyClient.Builder instance
     */
    public Builder metricsPort(int metricsPort) {
      this.metricsPort = metricsPort;
      return this;
    }

    /**
     * Invoke this method to enable sending distribution to Wavefront cluster via proxy
     *
     * @param distributionPort   Distribution Port on which the Wavefront proxy is listening on
     * @return WavefrontProxyClient.Builder instance
     */
    public Builder distributionPort(int distributionPort) {
      this.distributionPort = distributionPort;
      return this;
    }

    /**
     * Invoke this method to enable sending tracing spans to Wavefront cluster via proxy
     *
     * @param tracingPort        Tracing Port on which the Wavefront proxy is listening on
     * @return WavefrontProxyClient.Builder instance
     */
    public Builder tracingPort(int tracingPort) {
      this.tracingPort = tracingPort;
      return this;
    }

    /**
     * Set an explicit SocketFactory
     *
     * @param socketFactory       SocketFactory
     * @return WavefrontProxyClient.Builder instance
     */
    public Builder socketFactory(SocketFactory socketFactory) {
      this.socketFactory = socketFactory;
      return this;
    }

    /**
     * Builds WavefrontProxyClient instance
     *
     * @return WavefrontProxyClient instance
     * @throws UnknownHostException
     */
    public WavefrontProxyClient build() throws UnknownHostException {
      return new WavefrontProxyClient(this);
    }
  }

  private WavefrontProxyClient(Builder builder) throws UnknownHostException {
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
  }

  @Override
  public void sendMetric(String name, double value, @Nullable Long timestamp,
                         @Nullable String source, @Nullable Map<String, String> tags)
      throws IOException {
    if (!metricsProxyConnectionHandler.isConnected()) {
      try {
        metricsProxyConnectionHandler.connect();
      } catch (IllegalStateException ex) {
        // already connected.
      }
    }

    try {
      try {
        String lineData = metricToLineData(name, value, timestamp, source, tags, defaultSource);
        metricsProxyConnectionHandler.sendData(lineData);
      } catch (Exception e) {
        throw new IOException(e);
      }
    } catch (IOException e) {
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
      try {
        histogramProxyConnectionHandler.sendData(lineData);
      } catch (Exception e) {
        throw new IOException(e);
      }
    } catch (IOException e) {
      histogramProxyConnectionHandler.incrementFailureCount();
      throw e;
    }
  }

  @Override
  public void sendSpan(String name, long startMillis, long durationMicros,
                       @Nullable String source, UUID traceId, UUID spanId,
                       @Nullable List<UUID> parents, @Nullable List<UUID> followsFrom,
                       @Nullable List<Pair<String, String>> tags, @Nullable List<SpanLog> spanLogs)
      throws IOException {
    if (!tracingProxyConnectionHandler.isConnected()) {
      try {
        tracingProxyConnectionHandler.connect();
      } catch (IllegalStateException ex) {
        // already connected.
      }
    }

    try {
      String lineData = tracingSpanToLineData(name, startMillis, durationMicros, source, traceId,
          spanId, parents, followsFrom, tags, spanLogs, defaultSource);
      try {
        tracingProxyConnectionHandler.sendData(lineData);
      } catch (Exception e) {
        throw new IOException(e);
      }
    } catch (IOException e) {
      tracingProxyConnectionHandler.incrementFailureCount();
      throw e;
    }
  }

  @Override
  public void connect() throws IllegalStateException, IOException {
    if (metricsProxyConnectionHandler != null) {
      metricsProxyConnectionHandler.connect();
    }

    if (histogramProxyConnectionHandler != null) {
      histogramProxyConnectionHandler.connect();
    }

    if (tracingProxyConnectionHandler != null) {
      tracingProxyConnectionHandler.connect();
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
  public boolean isConnected() {
    if (metricsProxyConnectionHandler == null && histogramProxyConnectionHandler == null &&
        tracingProxyConnectionHandler == null) {
      // At least one proxy port to accept Wavefront entities needs to be open
      return false;
    }

    if (metricsProxyConnectionHandler != null && !metricsProxyConnectionHandler.isConnected()) {
      return false;
    }

    if (histogramProxyConnectionHandler != null && !histogramProxyConnectionHandler.isConnected()) {
      return false;
    }

    if (tracingProxyConnectionHandler != null && !tracingProxyConnectionHandler.isConnected()) {
      return false;
    }

    // default
    return true;
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
  public void close() throws IOException {
    if (metricsProxyConnectionHandler != null) {
      metricsProxyConnectionHandler.close();
    }

    if (histogramProxyConnectionHandler != null) {
      histogramProxyConnectionHandler.close();
    }

    if (tracingProxyConnectionHandler != null) {
      tracingProxyConnectionHandler.close();
    }
  }
}
