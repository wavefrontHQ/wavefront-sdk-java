package com.wavefront.sdk.common;

import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.tracing.SpanLog;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import static org.apache.commons.lang.StringUtils.isBlank;

/**
 * Common Util methods
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class Utils {

  private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");

  public static String sanitize(String s) {
    final String whitespaceSanitized = WHITESPACE.matcher(s).replaceAll("-");
    if (s.contains("\"") || s.contains("'")) {
      // for single quotes, once we are double-quoted, single quotes can exist happily inside it.
      return "\"" + whitespaceSanitized.replaceAll("\"", "\\\\\"") + "\"";
    } else {
      return "\"" + whitespaceSanitized + "\"";
    }
  }

  public static String metricToLineData(String name, double value, @Nullable Long timestamp,
                                        String source, @Nullable Map<String, String> tags,
                                        String defaultSource) {
    /*
     * Wavefront Metrics Data format
     * <metricName> <metricValue> [<timestamp>] source=<source> [pointTags]
     *
     * Example: "new-york.power.usage 42422 1533531013 source=localhost datacenter=dc1"
     */

    if (isBlank(name)) {
      throw new IllegalArgumentException("metrics name cannot be blank");
    }

    if (isBlank(source)) {
      source = defaultSource;
    }

    final StringBuilder sb = new StringBuilder();
    sb.append(sanitize(name));
    sb.append(' ');
    sb.append(Double.toString(value));
    if (timestamp != null) {
      sb.append(' ');
      sb.append(Long.toString(timestamp));
    }
    sb.append(" source=");
    sb.append(sanitize(source));
    if (tags != null) {
      for (final Map.Entry<String, String> tag : tags.entrySet()) {
        if (isBlank(tag.getKey())) {
          throw new IllegalArgumentException("metric point tag key cannot be blank");
        }
        if (isBlank(tag.getValue())) {
          throw new IllegalArgumentException("metric point tag value cannot be blank");
        }
        sb.append(' ');
        sb.append(sanitize(tag.getKey()));
        sb.append('=');
        sb.append(sanitize(tag.getValue()));
      }
    }
    sb.append('\n');
    return sb.toString();
  }

  public static String histogramToLineData(String name, List<Pair<Double, Integer>> centroids,
                                           Set<HistogramGranularity> histogramGranularities,
                                           @Nullable Long timestamp, String source,
                                           @Nullable Map<String, String> tags,
                                           String defaultSource) {
    /*
     * Wavefront Histogram Data format
     * {!M | !H | !D} [<timestamp>] #<count> <mean> [centroids] <histogramName> source=<source>
     *   [pointTags]
     *
     * Example: "!M 1533531013 #20 30.0 #10 5.1 request.latency source=appServer1 region=us-west"
     */

    if (isBlank(name)) {
      throw new IllegalArgumentException("histogram name cannot be blank");
    }

    if (histogramGranularities == null || histogramGranularities.isEmpty()) {
      throw new IllegalArgumentException("Histogram granularities cannot be null or empty");
    }

    if (centroids == null || centroids.isEmpty()) {
      throw new IllegalArgumentException("A distribution should have at least one centroid");
    }

    if (isBlank(source)) {
      source = defaultSource;
    }

    final StringBuilder sb = new StringBuilder();
    for (HistogramGranularity histogramGranularity : histogramGranularities) {
      sb.append(histogramGranularity.identifier);
      if (timestamp != null) {
        sb.append(' ');
        sb.append(Long.toString(timestamp));
      }
      for (Pair<Double, Integer> centroid : centroids) {
        sb.append(" #");
        sb.append(centroid._2);
        sb.append(' ');
        sb.append(centroid._1);
      }
      sb.append(' ');
      sb.append(sanitize(name));
      sb.append(" source=");
      sb.append(sanitize(source));
      if (tags != null) {
        for (final Map.Entry<String, String> tag : tags.entrySet()) {
          if (isBlank(tag.getKey())) {
            throw new IllegalArgumentException("histogram tag key cannot be blank");
          }
          if (isBlank(tag.getValue())) {
            throw new IllegalArgumentException("histogram tag value cannot be blank");
          }
          sb.append(' ');
          sb.append(sanitize(tag.getKey()));
          sb.append('=');
          sb.append(sanitize(tag.getValue()));
        }
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  public static String tracingSpanToLineData(String name, long startMillis, long durationMicros,
                                             String source, UUID traceId, UUID spanId,
                                             @Nullable List<UUID> parents,
                                             @Nullable List<UUID> followsFrom,
                                             @Nullable List<Pair<String, String>> tags,
                                             @Nullable List<SpanLog> spanLogs,
                                             String defaultSource) {
    /*
     * Wavefront Tracing Span Data format
     * <tracingSpanName> source=<source> [pointTags] <start_millis> <duration_micro_seconds>
     *
     * Example: "getAllUsers source=localhost
     *           traceId=7b3bf470-9456-11e8-9eb6-529269fb1459
     *           spanId=0313bafe-9457-11e8-9eb6-529269fb1459
     *           parent=2f64e538-9457-11e8-9eb6-529269fb1459
     *           application=Wavefront http.method=GET
     *           1533531013 343500"
     */

    if (isBlank(name)) {
      throw new IllegalArgumentException("span name cannot be blank");
    }

    if (isBlank(source)) {
      source = defaultSource;
    }

    final StringBuilder sb = new StringBuilder();
    sb.append(sanitize(name));
    sb.append(' ');
    sb.append(" source=");
    sb.append(sanitize(source));
    sb.append(" traceId=");
    sb.append(traceId);
    sb.append(" spanId=");
    sb.append(spanId);
    if (parents != null) {
      for (UUID parent : parents) {
        sb.append(" parent=");
        sb.append(parent.toString());
      }
    }
    if (followsFrom != null) {
      for (UUID item : followsFrom) {
        sb.append(" followsFrom=");
        sb.append(item.toString());
      }
    }
    if (tags != null) {
      for (final Pair<String, String> tag : tags) {
        if (isBlank(tag._1)) {
          throw new IllegalArgumentException("span tag key cannot be blank");
        }
        if (isBlank(tag._2)) {
          throw new IllegalArgumentException("span tag value cannot be blank");
        }
        sb.append(' ');
        sb.append(sanitize(tag._1));
        sb.append('=');
        sb.append(sanitize(tag._2));
      }
    }
    sb.append(' ');
    sb.append(startMillis);
    sb.append(' ');
    sb.append(durationMicros);
    // TODO - Support SpanLogs
    sb.append('\n');
    return sb.toString();
  }
}
