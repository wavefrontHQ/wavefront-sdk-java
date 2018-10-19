package com.wavefront.sdk.common;

import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.tracing.SpanLog;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

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

  /**
   * Generates the line-data representation of a metric point.
   *
   * @param name          The name of the metric.
   * @param value         The value of the metric.
   * @param timestamp     The timestamp in milliseconds since the epoch. Can be null.
   * @param source        The source (or host) of the metric.
   *                      If null, {@code defaultSource} is used.
   * @param tags          The tags associated with this metric. Can be null.
   * @param defaultSource The source to default to if {@code source} is null or empty.
   * @return returns the metric point in line-data format.
   */
  public static String metricToLineData(String name, double value, Long timestamp,
                                        String source, Map<String, String> tags,
                                        String defaultSource) {
    /*
     * Wavefront Metrics Data format
     * <metricName> <metricValue> [<timestamp>] source=<source> [pointTags]
     *
     * Example: "new-york.power.usage 42422 1533531013 source=localhost datacenter=dc1"
     */

    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("metrics name cannot be blank");
    }

    if (source == null || source.isEmpty()) {
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
        String key = tag.getKey();
        String val = tag.getValue();
        if (key == null || key.isEmpty()) {
          throw new IllegalArgumentException("metric point tag key cannot be blank");
        }
        if (val == null || val.isEmpty()) {
          throw new IllegalArgumentException("metric point tag value cannot be blank");
        }
        sb.append(' ');
        sb.append(sanitize(key));
        sb.append('=');
        sb.append(sanitize(val));
      }
    }
    sb.append('\n');
    return sb.toString();
  }

  /**
   * Generates the line-data representation of a histogram distribution.
   *
   * @param name                    The name of the distribution.
   * @param centroids               The distribution of histogram points.
   *                                Each centroid is a 2-dimensional {@link Pair} where the
   *                                first dimension is the mean value (Double) of the centroid
   *                                and second dimension is the count of points in that centroid.
   * @param histogramGranularities  The set of intervals (minute, hour, and/or day) by which
   *                                histogram data should be aggregated.
   * @param timestamp               The timestamp in milliseconds since the epoch. Can be null.
   * @param source                  The source (or host) of the distribution.
   *                                If null, {@code defaultSource} is used.
   * @param tags                    The tags associated with this distribution. Can be null.
   * @param defaultSource           The source to default to if {@code source} is null or empty.
   * @return returns the distribution in line-data format.
   */
  public static String histogramToLineData(String name, List<Pair<Double, Integer>> centroids,
                                           Set<HistogramGranularity> histogramGranularities,
                                           Long timestamp, String source, Map<String, String> tags,
                                           String defaultSource) {
    /*
     * Wavefront Histogram Data format
     * {!M | !H | !D} [<timestamp>] #<count> <mean> [centroids] <histogramName> source=<source>
     *   [pointTags]
     *
     * Example: "!M 1533531013 #20 30.0 #10 5.1 request.latency source=appServer1 region=us-west"
     */

    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("histogram name cannot be blank");
    }

    if (histogramGranularities == null || histogramGranularities.isEmpty()) {
      throw new IllegalArgumentException("Histogram granularities cannot be null or empty");
    }

    if (centroids == null || centroids.isEmpty()) {
      throw new IllegalArgumentException("A distribution should have at least one centroid");
    }

    if (source == null || source.isEmpty()) {
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
          String key = tag.getKey();
          String val = tag.getValue();
          if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("histogram tag key cannot be blank");
          }
          if (val == null || val.isEmpty()) {
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

  /**
   * Generates the line-data representation of a tracing span.
   *
   * @param name            The operation name of the span.
   * @param startMillis     The start time in milliseconds for this span.
   * @param durationMillis  The duration of the span in milliseconds.
   * @param source          The source (or host) that's sending the span.
   *                        If null, {@code defaultSource} is used.
   * @param traceId         The unique trace ID for the span.
   * @param spanId          The unique span ID for the span.
   * @param parents         The list of parent span IDs, can be null if this is a root span.
   * @param followsFrom     The list of preceding span IDs, can be null if this is a root span.
   * @param tags            The span tags associated with this span. Supports repeated tags.
   *                        Can be null.
   * @param spanLogs        The span logs associated with this span. Can be null.
   * @param defaultSource   The source to default to if {@code source} is null or empty.
   * @return returns the span in line-data format.
   */
  public static String tracingSpanToLineData(String name, long startMillis, long durationMillis,
                                             String source, UUID traceId, UUID spanId,
                                             List<UUID> parents, List<UUID> followsFrom,
                                             List<Pair<String, String>> tags,
                                             List<SpanLog> spanLogs,
                                             String defaultSource) {
    /*
     * Wavefront Tracing Span Data format
     * <tracingSpanName> source=<source> [pointTags] <start_millis> <duration_milli_seconds>
     *
     * Example: "getAllUsers source=localhost
     *           traceId=7b3bf470-9456-11e8-9eb6-529269fb1459
     *           spanId=0313bafe-9457-11e8-9eb6-529269fb1459
     *           parent=2f64e538-9457-11e8-9eb6-529269fb1459
     *           application=Wavefront http.method=GET
     *           1533531013 343500"
     */

    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("span name cannot be blank");
    }

    if (source == null || source.isEmpty()) {
      source = defaultSource;
    }

    final StringBuilder sb = new StringBuilder();
    sb.append(sanitize(name));
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
        if (tag._1 == null || tag._1.isEmpty()) {
          throw new IllegalArgumentException("span tag key cannot be blank");
        }
        if (tag._2 == null || tag._2.isEmpty()) {
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
    sb.append(durationMillis);
    // TODO - Support SpanLogs
    sb.append('\n');
    return sb.toString();
  }
}
