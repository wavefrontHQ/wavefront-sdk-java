package com.wavefront.sdk.common.metrics;

import com.wavefront.sdk.common.Constants;
import com.wavefront.sdk.common.NamedThreadFactory;
import com.wavefront.sdk.common.Utils;
import com.wavefront.sdk.entities.metrics.WavefrontMetricSender;

import java.io.Closeable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Metrics registry used to send internal SDK metrics to Wavefront.
 *
 * @author Han Zhang (zhanghan@vmware.com).
 * @version $Id: $Id
 */
public class WavefrontSdkMetricsRegistry implements Runnable, Closeable {
  private static final Logger logger = Logger.getLogger(
      WavefrontSdkMetricsRegistry.class.getCanonicalName());

  private final WavefrontMetricSender wavefrontMetricSender;
  private final String source;
  private final Map<String, String> tags;
  private final String prefix;
  private final ConcurrentMap<String, WavefrontSdkMetric> metrics;
  private final ScheduledExecutorService scheduler;
  private final boolean sendSdkMetrics;

  public static class Builder {
    // Required parameters
    private final WavefrontMetricSender wavefrontMetricSender;

    // Optional parameters
    private final Map<String, String> tags;
    private int reportingIntervalSeconds = 60;
    private boolean sendSdkMetrics = true;
    private String source;
    private String prefix;

    /**
     * Constructor.
     *
     * @param wavefrontMetricSender The sender instance used to send metrics to Wavefront.
     */
    public Builder(WavefrontMetricSender wavefrontMetricSender) {
      this.wavefrontMetricSender = wavefrontMetricSender;
      tags = new HashMap<>();
    }

    /**
     * Sets the interval in seconds at which to report metrics to Wavefront.
     *
     * @param reportingIntervalSeconds  Interval at which to report metrics to Wavefront.
     * @return {@code this}
     */
    public Builder reportingIntervalSeconds(int reportingIntervalSeconds) {
      this.reportingIntervalSeconds = reportingIntervalSeconds;
      return this;
    }

    /**
     * Sets the source (or host) that is sending the metrics.
     *
     * @param source  The source (or host) that is sending the metrics.
     * @return {@code this}
     */
    public Builder source(String source) {
      this.source = source;
      return this;
    }

    /**
     * Adds point tags associated with the registry's metrics.
     *
     * @param tags  The point tags associated with the registry's metrics.
     * @return {@code this}
     */
    public Builder tags(Map<String, String> tags) {
      this.tags.putAll(tags);
      return this;
    }

    /**
     * Adds a point tag associated with the registry's metrics.
     *
     * @param key   The tag key.
     * @param value The tag value.
     * @return {@code this}
     */
    public Builder tag(String key, String value) {
      this.tags.put(key, value);
      return this;
    }

    /**
     * Sets the name prefix for the registry's metrics.
     *
     * @param prefix  The name prefix for the registry's metrics.
     * @return {@code this}
     */
    public Builder prefix(String prefix) {
      this.prefix = prefix;
      return this;
    }

    /**
     * If this is set to false the sender will never actually send metrics and will simply flush metrics
     * @param sendSdkMetrics boolean
     * @return {@code this}
     */
    public Builder sendSdkMetrics(boolean sendSdkMetrics) {
      this.sendSdkMetrics = sendSdkMetrics;
      return this;
    }

    /**
     * Builds a registry.
     *
     * @return  A new instance of the registry.
     */
    public WavefrontSdkMetricsRegistry build() {
      return new WavefrontSdkMetricsRegistry(this);
    }
  }

  private WavefrontSdkMetricsRegistry(Builder builder) {
    wavefrontMetricSender = builder.wavefrontMetricSender;
    source = builder.source;
    tags = builder.tags;
    prefix = builder.prefix == null || builder.prefix.isEmpty() ? "" : builder.prefix + ".";
    sendSdkMetrics = builder.sendSdkMetrics;
    metrics = new ConcurrentHashMap<>();
    if (sendSdkMetrics) {
      scheduler = Executors.newScheduledThreadPool(1,
          new NamedThreadFactory("sdk-metrics-registry").setDaemon(true));
      scheduler.scheduleAtFixedRate(this, builder.reportingIntervalSeconds,
          builder.reportingIntervalSeconds, TimeUnit.SECONDS);
    } else {
      scheduler = null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    long timestamp = System.currentTimeMillis();
    for (Map.Entry<String, WavefrontSdkMetric> entry : metrics.entrySet()) {
      String name = prefix + entry.getKey();
      WavefrontSdkMetric metric = entry.getValue();
      try {
        if (metric instanceof WavefrontSdkGauge) {
          if (((WavefrontSdkGauge)metric).getValue() instanceof Number) {
            Number value = (Number) ((WavefrontSdkGauge) metric).getValue();
            wavefrontMetricSender.sendMetric(name, value.doubleValue(), timestamp, source, tags);
          }
        }  else if (metric instanceof WavefrontSdkDeltaCounter) {
          long count = ((WavefrontSdkDeltaCounter) metric).count();
          wavefrontMetricSender.sendDeltaCounter(name + ".count", count, timestamp, source, tags);
          ((WavefrontSdkDeltaCounter) metric).dec(count);
        } else if (metric instanceof WavefrontSdkCounter) {
          wavefrontMetricSender.sendMetric(name + ".count", ((WavefrontSdkCounter)metric).count(),
              timestamp, source, tags);
        }
      } catch (Throwable e) {
        logger.log(Level.WARNING, "Unable to send internal SDK metric", e);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void close() {
    if (sendSdkMetrics) {
      Utils.shutdownExecutorAndWait(scheduler);
    }
  }

  /**
   * Returns the gauge registered under the given name. If no metric is registered under this
   * name, create and register a new gauge using the given supplier.
   *
   * @param name      The metric name.
   * @param supplier  The supplier used to supply the value of the gauge.
   * @param <T>       The type of the value returned by the gauge.
   * @return A new or pre-existing gauge.
   */
  public <T> WavefrontSdkGauge<T> newGauge(String name, Supplier<T> supplier) {
    return getOrAdd(name, supplier::get);
  }

  /**
   * Returns the counter registered under the given name. If no metric is registered under this
   * name, create and register a new counter.
   *
   * @param name  The metric name.
   * @return A new or pre-existing counter.
   */
  public WavefrontSdkCounter newCounter(String name) {
    return getOrAdd(name, new WavefrontSdkCounter());
  }

  /**
   * Returns the ∆ counter registered under the given name. If no metric is registered under this
   * name, create and register a new counter. ∆ Prefix will be added to Delta Counter name.
   *
   * @param name  The metric name.
   * @return A new or pre-existing counter.
   */
  public WavefrontSdkDeltaCounter newDeltaCounter(String name) {
    return getOrAdd(name, new WavefrontSdkDeltaCounter());
  }

  @SuppressWarnings("unchecked")
  private <T extends WavefrontSdkMetric> T getOrAdd(String name, T metric) {
    WavefrontSdkMetric existingMetric = metrics.get(name);
    if (existingMetric == null) {
      existingMetric = metrics.putIfAbsent(name, metric);
      return existingMetric == null ? metric : (T) existingMetric;
    }
    return (T) existingMetric;
  }
}
