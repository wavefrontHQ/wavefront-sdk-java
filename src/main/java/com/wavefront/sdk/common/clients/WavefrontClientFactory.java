package com.wavefront.sdk.common.clients;

import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.annotation.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WavefrontClientFactory {
  private static final String PROXY_SCHEME = "proxy";
  private static final String HTTP_PROXY_SCHEME = "http";
  private static final String DIRECT_DATA_INGESTION_SCHEME = "https";

  private List<WavefrontSender> clients = new ArrayList<>();
  private static Logger log = Logger.getLogger(WavefrontClientFactory.class.getCanonicalName());


  /**
   * Adds an existing WavefrontSender that is configured to
   * forward points to a proxy or directly to a Wavefront service.
   *
   * @param sender the sender to add to this factory
   *
   * @return
   * Returns {@link WavefrontClientFactory} so that more clients may be initialized
   */
  public WavefrontClientFactory addClient(WavefrontSender sender) {
    if (!existingClient(sender.getClientId())) {
      clients.add(sender);
    }

    return this;
  }

  /**
   * Adds a new WavefrontSender that will either forward points to a proxy or directly to a Wavefront service.
   * <br />
   * <strong>proxy ingestion:</strong> <i>proxy://your.proxy.com:port</i> <br />
   * <strong>direct ingestion:</strong> <i>https://token@yourCluster.wavefront.com</i> <br />
   * <br />
   *
   * @param url The URL of either yourCluster or your.proxy.com
   *
   * @return
   * Returns {@link WavefrontClientFactory} so that more clients may be initialized
   */
  public WavefrontClientFactory addClient(String url) {
    return addClient(url, null, null, null, null);
  }

  /**
   * Adds a new WavefrontSender that will either forward points to a proxy or directly to a Wavefront service.
   * <br />
   * <strong>proxy ingestion:</strong> <i>proxy://your.proxy.com:port</i> <br />
   * <strong>direct ingestion:</strong> <i>https://token@yourCluster.wavefront.com</i> <br />
   * <br />
   *
   * @param url The URL of either yourCluster or your.proxy.com
   * @param batchSize The total metrics, histograms, spans, or span logs to send in a single flush
   * @param maxQueueSize The total metrics, histograms, spans, or span logs to queue internally before dropping data
   * @param flushIntervalSeconds  How often to flush data upstream
   * @return
   * Returns {@link WavefrontClientFactory} so that more clients may be initialized
   */
  public WavefrontClientFactory addClient(String url, @Nullable Integer batchSize, @Nullable Integer maxQueueSize,
                                          @Nullable Integer flushIntervalSeconds, @Nullable Integer messageSizeInBytes) {
    return addClient(url, batchSize, maxQueueSize, flushIntervalSeconds, messageSizeInBytes, true);
  }

  /**
   * Adds a new WavefrontSender that will either forward points to a proxy or directly to a Wavefront service.
   * <br />
   * <strong>proxy ingestion:</strong> <i>proxy://your.proxy.com:port</i> <br />
   * <strong>direct ingestion:</strong> <i>https://token@yourCluster.wavefront.com</i> <br />
   * <br />
   *
   * @param url The URL of either yourCluster or your.proxy.com
   * @param batchSize The total metrics, histograms, spans, or span logs to send in a single flush
   * @param maxQueueSize The total metrics, histograms, spans, or span logs to queue internally before dropping data
   * @param flushIntervalSeconds  How often to flush data upstream
   * @param includeSdkMetrics Whether or not to include the internal SDK Metrics
   * @return
   * Returns {@link WavefrontClientFactory} so that more clients may be initialized
   */
  public WavefrontClientFactory addClient(String url, @Nullable Integer batchSize, @Nullable Integer maxQueueSize,
                                          @Nullable Integer flushIntervalSeconds, @Nullable Integer messageSizeInBytes,
                                          boolean includeSdkMetrics) {
    return addClient(url, batchSize, maxQueueSize, flushIntervalSeconds, messageSizeInBytes, includeSdkMetrics, null);
  }

  /**
   * Adds a new WavefrontSender that will either forward points to a proxy or directly to a Wavefront service.
   * <br />
   * <strong>proxy ingestion:</strong> <i>proxy://your.proxy.com:port</i> <br />
   * <strong>direct ingestion:</strong> <i>https://token@yourCluster.wavefront.com</i> <br />
   * <br />
   *
   * @param url The URL of either yourCluster or your.proxy.com
   * @param batchSize The total metrics, histograms, spans, or span logs to send in a single flush
   * @param maxQueueSize The total metrics, histograms, spans, or span logs to queue internally before dropping data
   * @param flushIntervalSeconds  How often to flush data upstream
   * @param includeSdkMetrics Whether or not to include the internal SDK Metrics
   * @param sdkMetricTags a map of tags to include on the internal sdk metrics if included
   * @return
   * Returns {@link WavefrontClientFactory} so that more clients may be initialized
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
   * <br />
   * <strong>proxy ingestion:</strong> <i>proxy://your.proxy.com:port</i> <br />
   * <strong>direct ingestion:</strong> <i>https://token@yourCluster.wavefront.com</i> <br />
   * <br />
   *
   * @param url The URL of either yourCluster or your.proxy.com
   * @param batchSize The total metrics, histograms, spans, or span logs to send in a single flush
   * @param maxQueueSize The total metrics, histograms, spans, or span logs to queue internally before dropping data
   * @param flushInterval How often to flush data upstream
   * @param flushIntervalTimeUnit Time unit of the flush interval
   * @param includeSdkMetrics Whether or not to include the internal SDK Metrics
   * @param sdkMetricTags a map of tags to include on the internal sdk metrics if included
   * @return {@link WavefrontClientFactory} so that more clients may be initialized
   */
  public WavefrontClientFactory addClient(String url, @Nullable Integer batchSize,
                                          @Nullable Integer maxQueueSize,
                                          @Nullable Integer flushInterval,
                                          @Nullable TimeUnit flushIntervalTimeUnit,
                                          @Nullable Integer messageSizeInBytes,
                                          boolean includeSdkMetrics,
                                          @Nullable Map<String, String> sdkMetricTags) {
    ParsedHostString parsedHostString = getServerAndTokenFromEndpoint(url);
    if (existingClient(parsedHostString.server)) {
      throw new UnsupportedOperationException("client with id " + url + " already exists.");
    }

    WavefrontClient.Builder builder = new WavefrontClient.Builder(parsedHostString.server, parsedHostString.token);
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
   *
   * @return {@link WavefrontClient} or {@link WavefrontMultiClient}
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

  private ParsedHostString getServerAndTokenFromEndpoint(String endpoint) {
    URI uri = URI.create(endpoint);
    final String token;
    final String host;
    if (uri.getScheme().equals(DIRECT_DATA_INGESTION_SCHEME)) {
      token = uri.getUserInfo();
      host = DIRECT_DATA_INGESTION_SCHEME + "://" + uri.getHost();
    } else if (uri.getScheme().equals(PROXY_SCHEME) || uri.getScheme().equals(HTTP_PROXY_SCHEME)) {
      token = null;
      host = HTTP_PROXY_SCHEME + "://" + uri.getHost() + ":" + uri.getPort();
      if (uri.getUserInfo() != null) {
        log.log(Level.WARNING, "Attempting to send a token over clear-text, dropping token.");
      }
    } else {
      throw new RuntimeException("Unknown scheme specified while attempting to build a client " + uri.getScheme());
    }
    return new ParsedHostString(host, token);
  }

  private static class ParsedHostString {
    final String server;
    final String token;
    ParsedHostString(String server, String token) {
      this.server = server;
      this.token = token;
    }
  }
}
