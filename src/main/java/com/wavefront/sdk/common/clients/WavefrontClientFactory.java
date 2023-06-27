package com.wavefront.sdk.common.clients;

import com.google.common.annotations.VisibleForTesting;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.annotation.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>WavefrontClientFactory class.</p>
 *
 * @author goppegard
 * @version $Id: $Id
 */
public class WavefrontClientFactory {
  private static final Logger log = Logger.getLogger(WavefrontClientFactory.class.getCanonicalName());

  private static final String PROXY_SCHEME = "proxy";
  private static final String HTTP_SCHEME = "http";
  private static final String HTTPS_SCHEME = "https";

  private final List<WavefrontSender> clients = new ArrayList<>();


  /**
   * Adds an existing WavefrontSender that is configured to
   * forward points to a proxy or directly to a Wavefront service.
   *
   * @param sender the sender to add to this factory
   * @return a {@link com.wavefront.sdk.common.clients.WavefrontClientFactory} object
   */
  public WavefrontClientFactory addClient(WavefrontSender sender) {
    if (!existingClient(sender.getClientId())) {
      clients.add(sender);
    }

    return this;
  }

  /**
   * Adds a new WavefrontSender that will either forward points to a proxy or directly to a Wavefront service.
   * <br>
   * <strong>proxy ingestion:</strong> <i>proxy://your.proxy.com:port</i> <br>
   * <strong>direct ingestion:</strong> <i>https://token@yourCluster.wavefront.com</i> <br>
   * <br>
   *
   * @param url The URL of either yourCluster or your.proxy.com
   * @return a {@link com.wavefront.sdk.common.clients.WavefrontClientFactory} object
   */
  public WavefrontClientFactory addClient(String url) {
    return addClient(url, null, null, null, null);
  }

  /**
   * Adds a new WavefrontSender that will either forward points to a proxy or directly to a Wavefront service.
   * <br>
   * <strong>proxy ingestion:</strong> <i>proxy://your.proxy.com:port</i> <br>
   * <strong>direct ingestion:</strong> <i>https://token@yourCluster.wavefront.com</i> <br>
   * <br>
   *
   * @param url The URL of either yourCluster or your.proxy.com
   * @param batchSize The total metrics, histograms, spans, or span logs to send in a single flush
   * @param maxQueueSize The total metrics, histograms, spans, or span logs to queue internally before dropping data
   * @param flushIntervalSeconds  How often to flush data upstream
   * @param messageSizeInBytes a {@link java.lang.Integer} object
   * @return a {@link com.wavefront.sdk.common.clients.WavefrontClientFactory} object
   */
  public WavefrontClientFactory addClient(String url, @Nullable Integer batchSize, @Nullable Integer maxQueueSize,
                                          @Nullable Integer flushIntervalSeconds, @Nullable Integer messageSizeInBytes) {
    return addClient(url, batchSize, maxQueueSize, flushIntervalSeconds, messageSizeInBytes, true);
  }

  /**
   * Adds a new WavefrontSender that will either forward points to a proxy or directly to a Wavefront service.
   * <br>
   * <strong>proxy ingestion:</strong> <i>proxy://your.proxy.com:port</i> <br>
   * <strong>direct ingestion:</strong> <i>https://token@yourCluster.wavefront.com</i> <br>
   * <br>
   *
   * @param url The URL of either yourCluster or your.proxy.com
   * @param batchSize The total metrics, histograms, spans, or span logs to send in a single flush
   * @param maxQueueSize The total metrics, histograms, spans, or span logs to queue internally before dropping data
   * @param flushIntervalSeconds  How often to flush data upstream
   * @param includeSdkMetrics Whether or not to include the internal SDK Metrics
   * @param messageSizeInBytes a {@link java.lang.Integer} object
   * @return a {@link com.wavefront.sdk.common.clients.WavefrontClientFactory} object
   */
  public WavefrontClientFactory addClient(String url, @Nullable Integer batchSize, @Nullable Integer maxQueueSize,
                                          @Nullable Integer flushIntervalSeconds, @Nullable Integer messageSizeInBytes,
                                          boolean includeSdkMetrics) {
    return addClient(url, batchSize, maxQueueSize, flushIntervalSeconds, messageSizeInBytes, includeSdkMetrics, null);
  }

  /**
   * Adds a new WavefrontSender that will either forward points to a proxy or directly to a Wavefront service.
   * <br>
   * <strong>proxy ingestion:</strong> <i>proxy://your.proxy.com:port</i> <br>
   * <strong>direct ingestion:</strong> <i>https://token@yourCluster.wavefront.com</i> <br>
   * <br>
   *
   * @param url The URL of either yourCluster or your.proxy.com
   * @param batchSize The total metrics, histograms, spans, or span logs to send in a single flush
   * @param maxQueueSize The total metrics, histograms, spans, or span logs to queue internally before dropping data
   * @param flushIntervalSeconds  How often to flush data upstream
   * @param includeSdkMetrics Whether or not to include the internal SDK Metrics
   * @param sdkMetricTags a map of tags to include on the internal sdk metrics if included
   * @param messageSizeInBytes a {@link java.lang.Integer} object
   * @return a {@link com.wavefront.sdk.common.clients.WavefrontClientFactory} object
   */
  public WavefrontClientFactory addClient(String url, @Nullable Integer batchSize,
                                          @Nullable Integer maxQueueSize,
                                          @Nullable Integer flushIntervalSeconds,
                                          @Nullable Integer messageSizeInBytes,
                                          boolean includeSdkMetrics,
                                          @Nullable Map<String, String> sdkMetricTags) {
    return addClient(url, batchSize, maxQueueSize, flushIntervalSeconds, TimeUnit.SECONDS,
        messageSizeInBytes, includeSdkMetrics, sdkMetricTags);
  }

  /**
   * Adds a new WavefrontSender that will either forward points to a proxy or directly to a Wavefront service.
   * <br>
   * <strong>proxy ingestion:</strong> <i>proxy://your.proxy.com:port</i> <br>
   * <strong>direct ingestion:</strong> <i>https://token@yourCluster.wavefront.com</i> <br>
   * <br>
   *
   * @param url The URL of either yourCluster or your.proxy.com
   * @param batchSize The total metrics, histograms, spans, or span logs to send in a single flush
   * @param maxQueueSize The total metrics, histograms, spans, or span logs to queue internally before dropping data
   * @param flushInterval How often to flush data upstream
   * @param flushIntervalTimeUnit Time unit of the flush interval
   * @param includeSdkMetrics Whether or not to include the internal SDK Metrics
   * @param sdkMetricTags a map of tags to include on the internal sdk metrics if included
   * @return {@link com.wavefront.sdk.common.clients.WavefrontClientFactory} so that more clients may be initialized
   * @param messageSizeInBytes a {@link java.lang.Integer} object
   */
  public WavefrontClientFactory addClient(String url, @Nullable Integer batchSize,
                                          @Nullable Integer maxQueueSize,
                                          @Nullable Integer flushInterval,
                                          @Nullable TimeUnit flushIntervalTimeUnit,
                                          @Nullable Integer messageSizeInBytes,
                                          boolean includeSdkMetrics,
                                          @Nullable Map<String, String> sdkMetricTags) {
    Pair<String, String> serverToken = parseEndpoint(url);
    if (existingClient(serverToken._1)) {
      throw new UnsupportedOperationException("client with id " + url + " already exists.");
    }

    WavefrontClient.Builder builder = new WavefrontClient.Builder(serverToken._1, serverToken._2);
    if (batchSize != null) {
      builder.batchSize(batchSize);
    }
    if (maxQueueSize != null) {
      builder.maxQueueSize(maxQueueSize);
    }
    if (flushInterval != null && flushIntervalTimeUnit != null) {
      builder.flushInterval(flushInterval, flushIntervalTimeUnit);
    }
    if (messageSizeInBytes != null) {
      builder.messageSizeBytes(messageSizeInBytes);
    }
    builder.includeSdkMetrics(includeSdkMetrics);
    if (includeSdkMetrics && sdkMetricTags != null && sdkMetricTags.size() > 0) {
      builder.sdkMetricsTags(sdkMetricTags);
    }

    clients.add(builder.build());
    return this;
  }

  /**
   * <p>getClient.</p>
   *
   * @return {@link com.wavefront.sdk.common.clients.WavefrontClient} or {@link com.wavefront.sdk.common.clients.WavefrontMultiClient}
   */
  public WavefrontSender getClient() {
    if (clients.size() == 0) {
      log.log(Level.WARNING, "Call to getClient without any endpoints having been specified");
      return null;
    }
    if (clients.size() == 1) {
      return clients.get(0);
    }
    WavefrontMultiClient.Builder builder = new WavefrontMultiClient.Builder();
    clients.forEach(builder::withWavefrontSender);
    return builder.build();
  }

  private boolean existingClient(String server) {
    return clients.stream().anyMatch(c -> c.getClientId().equals(server));
  }

  @VisibleForTesting
  static Pair<String, String> parseEndpoint(String endpoint) {
    URI uri = URI.create(endpoint);
    final String scheme = PROXY_SCHEME.equals(uri.getScheme()) ? HTTP_SCHEME : uri.getScheme();
    if (!scheme.equals(HTTP_SCHEME) && !scheme.equals(HTTPS_SCHEME)) {
      throw new IllegalArgumentException("Unknown scheme (" + scheme + ") specified " +
          "while attempting to build a client " + endpoint);
    }
    String token = uri.getUserInfo();
    if (token != null && scheme.equals(HTTP_SCHEME)) {
      log.log(Level.WARNING, "Attempting to send a token over clear-text, dropping token.");
      token = null;
    }
    StringBuilder host = new StringBuilder(scheme).append("://").append(uri.getHost());
    if (uri.getPort() > 0) {
      host.append(":").append(uri.getPort());
    }
    return Pair.of(host.toString(), token);
  }
}
