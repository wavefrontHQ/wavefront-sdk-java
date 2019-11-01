package com.wavefront.sdk.common.application;

import com.wavefront.sdk.common.Constants;
import com.wavefront.sdk.common.NamedThreadFactory;
import com.wavefront.sdk.common.Utils;
import com.wavefront.sdk.entities.metrics.WavefrontMetricSender;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

/**
 * Service that periodically reports component heartbeats to Wavefront.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class HeartbeaterService implements Runnable, Closeable {
  private static final Logger logger = Logger.getLogger(
      HeartbeaterService.class.getCanonicalName());
  private final WavefrontMetricSender wavefrontMetricSender;
  private final List<Map<String, String>> heartbeatMetricTagsList = new ArrayList<>();
  private final ScheduledExecutorService scheduler;
  private final String source;
  private final Set<Map<String, String>> customTagsSet =
      Collections.newSetFromMap(new ConcurrentHashMap<>());

  public HeartbeaterService(WavefrontMetricSender wavefrontMetricSender,
                            ApplicationTags applicationTags,
                            List<String> components,
                            String source) {
    this.wavefrontMetricSender = wavefrontMetricSender;
    this.source = source;
    for (String component : components) {
      heartbeatMetricTagsList.add(new HashMap<String, String>() {{
        put(APPLICATION_TAG_KEY, applicationTags.getApplication());
        put(CLUSTER_TAG_KEY, applicationTags.getCluster() == null ? Constants.NULL_TAG_VAL :
            applicationTags.getCluster());
        put(SERVICE_TAG_KEY, applicationTags.getService());
        put(SHARD_TAG_KEY, applicationTags.getShard() == null ? Constants.NULL_TAG_VAL :
            applicationTags.getShard());
        put(COMPONENT_TAG_KEY, component);
        if (applicationTags.getCustomTags() != null) {
          putAll(applicationTags.getCustomTags());
        }
      }});
    }
    scheduler = Executors.newScheduledThreadPool(1,
        new NamedThreadFactory("heart-beater"));
    scheduler.scheduleAtFixedRate(this, 1, 5, TimeUnit.MINUTES);
  }

  public void reportCustomTags(Map<String, String> customTagsMap) {
    customTagsSet.add(customTagsMap);
  }

  @Override
  public void run() {
    Iterator<Map<String, String>> iter = customTagsSet.iterator();
    while (iter.hasNext()) {
      try {
        wavefrontMetricSender.sendMetric(Constants.HEART_BEAT_METRIC, 1.0,
            System.currentTimeMillis(), source, iter.next());
        iter.remove();
      } catch (Throwable t) {
        logger.warning("Cannot report custom " + Constants.HEART_BEAT_METRIC + " to Wavefront");
      }
    }

    for (Map<String, String> heartbeatMetricTags : heartbeatMetricTagsList) {
      try {
        wavefrontMetricSender.sendMetric(Constants.HEART_BEAT_METRIC, 1.0,
            System.currentTimeMillis(), source, heartbeatMetricTags);
      } catch (Throwable t) {
        logger.warning("Cannot report " + Constants.HEART_BEAT_METRIC + " to Wavefront");
      }
    }
  }

  @Override
  public void close() {
    try {
      Utils.shutdownExecutorAndWait(scheduler);
    } catch (SecurityException ex) {
      logger.log(Level.FINE, "shutdown error", ex);
    }
  }
}
