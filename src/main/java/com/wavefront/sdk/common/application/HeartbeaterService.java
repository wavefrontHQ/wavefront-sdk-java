package com.wavefront.sdk.common.application;

import com.wavefront.sdk.common.Constants;
import com.wavefront.sdk.common.NamedThreadFactory;
import com.wavefront.sdk.entities.metrics.WavefrontMetricSender;

import java.io.Closeable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.wavefront.sdk.common.Constants.APPLICATION_TAG_KEY;
import static com.wavefront.sdk.common.Constants.CLUSTER_TAG_KEY;
import static com.wavefront.sdk.common.Constants.COMPONENT_TAG_KEY;
import static com.wavefront.sdk.common.Constants.SERVICE_TAG_KEY;
import static com.wavefront.sdk.common.Constants.SHARD_TAG_KEY;
import static com.wavefront.sdk.common.Constants.WAVEFRONT_PROVIDED_SOURCE;

/**
 * Service that periodically reports component heartbeats to Wavefront.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class HeartbeaterService implements Runnable, Closeable {
  private static final Logger logger = Logger.getLogger(HeartbeaterService.class.getCanonicalName());
  private final WavefrontMetricSender wavefrontMetricSender;
  private final Map<String, String> heartbeatMetricTags;
  private final ScheduledExecutorService scheduler;

  public HeartbeaterService(WavefrontMetricSender wavefrontMetricSender,
                            ApplicationTags applicationTags,
                            String component) {
    this.wavefrontMetricSender = wavefrontMetricSender;
    this.heartbeatMetricTags = new HashMap<String, String>() {{
      put(APPLICATION_TAG_KEY, applicationTags.getApplication());
      put(CLUSTER_TAG_KEY, applicationTags.getCluster() == null ? Constants.NULL_TAG_VAL :
              applicationTags.getCluster());
      put(SERVICE_TAG_KEY, applicationTags.getService());
      put(SHARD_TAG_KEY, applicationTags.getShard() == null ? Constants.NULL_TAG_VAL : applicationTags.getShard());
      put(COMPONENT_TAG_KEY, component);
    }};
    scheduler = Executors.newScheduledThreadPool(1,
        new NamedThreadFactory(component + "-heart-beater"));
    scheduler.scheduleAtFixedRate(this, 1, 5, TimeUnit.MINUTES);
  }

  @Override
  public void run() {
    try {
      wavefrontMetricSender.sendMetric(Constants.HEART_BEAT_METRIC, 1.0,
          System.currentTimeMillis(), WAVEFRONT_PROVIDED_SOURCE, heartbeatMetricTags);
    } catch (Throwable t) {
      logger.warning("Cannot report " + Constants.HEART_BEAT_METRIC + " to Wavefront");
    }
  }

  @Override
  public void close() {
    try {
      scheduler.shutdownNow();
    } catch (SecurityException ex) {
      logger.log(Level.FINE, "shutdown error", ex);
    }
  }
}
