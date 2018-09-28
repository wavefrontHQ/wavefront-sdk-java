package com.wavefront.sdk.common.application;

import com.wavefront.sdk.common.Constants;
import com.wavefront.sdk.common.NamedThreadFactory;
import com.wavefront.sdk.entities.metrics.WavefrontMetricSender;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.wavefront.sdk.common.Constants.WAVEFRONT_PROVIDED_SOURCE;

/**
 * Service that periodically reports component heartbeats to Wavefront.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class HeartbeaterService implements Runnable {
  private static final Logger logger = Logger.getLogger(HeartbeaterService.class.getCanonicalName());
  private final WavefrontMetricSender wavefrontMetricSender;
  private final Map<String, String> heartbeatMetricTags;

  public HeartbeaterService(WavefrontMetricSender wavefrontMetricSender,
                            ApplicationTags applicationTags,
                            String component) {
    this.wavefrontMetricSender = wavefrontMetricSender;
    this.heartbeatMetricTags = new HashMap<String, String>() {{
      put("application", applicationTags.getApplication());
      put("cluster", applicationTags.getCluster() == null ? Constants.NULL_TAG_VAL :
              applicationTags.getCluster());
      put("service", applicationTags.getService());
      put("shard", applicationTags.getShard() == null ? Constants.NULL_TAG_VAL : applicationTags.getShard());
      put("component", component);
    }};
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1,
        new NamedThreadFactory(component + "-heart-beater"));
    scheduler.scheduleAtFixedRate(this, 1, 5, TimeUnit.MINUTES);
  }

  @Override
  public void run() {
    try {
      wavefrontMetricSender.sendMetric(Constants.HEART_BEAT_METRIC, 1.0,
          System.currentTimeMillis(), WAVEFRONT_PROVIDED_SOURCE, heartbeatMetricTags);
    } catch (IOException e) {
      logger.warning("Cannot report " + Constants.HEART_BEAT_METRIC + " to Wavefront");
    }
  }
}
