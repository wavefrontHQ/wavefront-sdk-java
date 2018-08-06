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

  /**
   * Creates a new WavefrontProxyClient client that connects to given address and socket factory
   *
   * @param agentHostName      The hostname of the Wavefront Proxy Agent
   * @param metricsPort        The metrics port of the Wavefront Proxy Agent
   * @param distributionPort   The distribution port of the Wavefront Proxy Agent
   * @param tracingPort        The tracing port of the Wavefront Proxy Agent
   */
  public WavefrontProxyClient(String agentHostName,
                              @Nullable Integer metricsPort,
                              @Nullable Integer distributionPort,
                              @Nullable Integer tracingPort) throws UnknownHostException {
    this(agentHostName, metricsPort, distributionPort, tracingPort, SocketFactory.getDefault());
  }

  /**
   * Creates a new WavefrontProxyClient client that connects to given address and socket factory
   *
   * @param agentHostName      The hostname of the Wavefront Proxy Agent
   * @param metricsPort        The metrics port of the Wavefront Proxy Agent
   * @param distributionPort   The distribution port of the Wavefront Proxy Agent
   * @param tracingPort        The tracing port of the Wavefront Proxy Agent
   * @param socketFactory      The socket factory
   */
  public WavefrontProxyClient(String agentHostName,
                              @Nullable Integer metricsPort,
                              @Nullable Integer distributionPort,
                              @Nullable Integer tracingPort,
                              SocketFactory socketFactory) throws UnknownHostException {
    if (metricsPort == null) {
      metricsProxyConnectionHandler = null;
    } else {
      metricsProxyConnectionHandler = new ProxyConnectionHandler(
          new InetSocketAddress(agentHostName, metricsPort), socketFactory);
    }

    if (distributionPort == null) {
      histogramProxyConnectionHandler = null;
    } else {
      histogramProxyConnectionHandler = new ProxyConnectionHandler(
          new InetSocketAddress(agentHostName, distributionPort), socketFactory);
    }

    if (tracingPort == null) {
      tracingProxyConnectionHandler = null;
    } else {
      tracingProxyConnectionHandler = new ProxyConnectionHandler(
          new InetSocketAddress(agentHostName, tracingPort), socketFactory);
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
