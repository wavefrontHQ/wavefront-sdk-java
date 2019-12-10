package com.wavefront.sdk.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wavefront.sdk.common.annotation.NonNull;
import com.wavefront.sdk.common.annotation.Nullable;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.tracing.SpanLog;
import com.wavefront.sdk.entities.tracing.SpanLogsDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.wavefront.sdk.common.Constants.SPAN_LOG_KEY;

/**
 * Common Util methods
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class Utils {

  private static final ObjectMapper JSON_PARSER = new ObjectMapper();

  public static String enquote(String s) {
    if (s.contains("\"") || s.contains("'")) {
      // for single quotes, once we are double-quoted, single quotes can exist happily inside it.
      s = s.replaceAll("\"", "\\\\\"");
    }
    return "\"" + s.replaceAll("\\n", "\\\\n") + "\"";
  }

  /**
   * @deprecated Use enquote(sanitizeName(s)) for metric name, and enquote(sanitizeKey) for source
   * and tag keys instead.
   */
  @Deprecated
  public static String sanitize(String s) {
    /*
     * Sanitize string of metric name, source and key of tags according to the rule of Wavefront
     * proxy
     */
    return enquote(sanitizeName(s));
  }

  public static String sanitizeName(String s) {
    return sanitizeKeyInternal(s, true);
  }

  public static String sanitizeKey(String s) {
    return sanitizeKeyInternal(s, false);
  }

  private static String sanitizeKeyInternal(String s, boolean isMetricName) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      sb.append(characterIsValid(s, i, isMetricName) ? s.charAt(i) : '-');
    }
    return sb.toString();
  }

  public static boolean isValidName(String s) {
    return isValidKeyInternal(s, true);
  }

  public static boolean isValidKey(String s) {
    return isValidKeyInternal(s, false);
  }

  private static boolean isValidKeyInternal(String s, boolean isMetricName) {
    for (int i = 0; i < s.length(); i++) {
      if (!characterIsValid(s, i, isMetricName)) {
        return false;
      }
    }
    return true;
  }

  private static boolean characterIsValid(String s, int i, boolean isMetricName) {
    char c = s.charAt(i);
    if ((44 <= c && c <= 57) || (65 <= c && c <= 90) || (97 <= c && c <= 122) || c == 95) {
      // Legal characters are 44-57 (,-./ and numbers), 65-90 (upper), 97-122 (lower), 95 (_)
      return true;
    } else if (isMetricName && i == 0 && (c == 0x2206 || c == 0x0394 || c == 126)) {
      // For metric names, first character can also be \u2206 (∆ - INCREMENT) or \u0394 (Δ -
      // GREEK CAPITAL LETTER DELTA) or ~ tilda character for internal metrics
      return true;
    }
    return false;
  }

  /**
   * @deprecated Use enquote(sanitizeVal(s)) instead.
   */
  @Deprecated
  public static String sanitizeValue(String s) {
    /*
     * Sanitize string of tags value, etc.
     */
    return enquote(sanitizeVal(s));
  }

  public static String sanitizeVal(String s) {
    return s.trim();
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

    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("metrics name cannot be blank");
    }

    if (source == null || source.isEmpty()) {
      source = defaultSource;
    }

    final StringBuilder sb = new StringBuilder();
    sb.append(enquote(sanitizeName(name)));
    sb.append(' ');
    sb.append(value);
    if (timestamp != null) {
      sb.append(' ');
      sb.append(timestamp);
    }
    sb.append(" source=");
    sb.append(enquote(sanitizeKey(source)));
    if (tags != null) {
      for (final Map.Entry<String, String> tag : tags.entrySet()) {
        String key = tag.getKey();
        String val = tag.getValue();
        if (key == null || key.isEmpty()) {
          throw new IllegalArgumentException("metric point tag key cannot be blank");
        }
        if (val == null || val.isEmpty()) {
          throw new IllegalArgumentException("metric point tag value cannot be blank for " +
              "tag key: " + key);
        }
        sb.append(' ');
        sb.append(enquote(sanitizeKey(key)));
        sb.append('=');
        sb.append(enquote(sanitizeVal(val)));
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
      sb.append(enquote(sanitizeName(name)));
      sb.append(" source=");
      sb.append(enquote(sanitizeKey(source)));
      if (tags != null) {
        for (final Map.Entry<String, String> tag : tags.entrySet()) {
          String key = tag.getKey();
          String val = tag.getValue();
          if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("histogram tag key cannot be blank");
          }
          if (val == null || val.isEmpty()) {
            throw new IllegalArgumentException("histogram tag value cannot be blank for " +
                "tag key: " + key);
          }
          sb.append(' ');
          sb.append(enquote(sanitizeName(tag.getKey())));
          sb.append('=');
          sb.append(enquote(sanitizeVal(tag.getValue())));
        }
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  public static String tracingSpanToLineData(String name, long startMillis, long durationMillis,
                                             String source, UUID traceId, UUID spanId,
                                             @Nullable List<UUID> parents,
                                             @Nullable List<UUID> followsFrom,
                                             @Nullable List<Pair<String, String>> tags,
                                             @Nullable List<SpanLog> spanLogs, String defaultSource) {
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
    sb.append(enquote(sanitizeVal(name)));
    sb.append(" source=");
    sb.append(enquote(sanitizeKey(source)));
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
        String key = tag._1;
        String val = tag._2;
        if (key == null || key.isEmpty()) {
          throw new IllegalArgumentException("span tag key cannot be blank");
        }
        if (val == null || val.isEmpty()) {
          throw new IllegalArgumentException("span tag value cannot be blank for " +
              "tag key: " + key);
        }
        sb.append(' ');
        sb.append(enquote(sanitizeKey(key)));
        sb.append('=');
        sb.append(enquote(sanitizeVal(val)));
      }
    }
    if (spanLogs != null  && !spanLogs.isEmpty()) {
      sb.append(' ');
      sb.append(enquote(sanitizeKey(SPAN_LOG_KEY)));
      sb.append('=');
      sb.append(enquote(sanitizeVal("true")));
    }
    sb.append(' ');
    sb.append(startMillis);
    sb.append(' ');
    sb.append(durationMillis);
    sb.append('\n');
    return sb.toString();
  }

  public static String spanLogsToLineData(UUID traceId, UUID spanId, @NonNull List<SpanLog> spanLogs)
      throws JsonProcessingException {
    /*
     * Wavefront Span Log Data format
     * Example:
     *  {
     *      "traceId": "7b3bf470-9456-11e8-9eb6-529269fb1459",
     *      "spanId": "0313bafe-9457-11e8-9eb6-529269fb1459",
     *      "logs": [
     *          {
     *              "timestamp": "1533531013",
     *              "fields": {
     *                  "event": "error",
     *                  "error.kind": "exception",
     *                  "message": "timed out",
     *                  "stack": "File \"example.py\", line 7, in \<module\>\ncaller()\nFile \"example.py\""
     *              }
     *          }
     *      ]
     *  }
     */

    StringBuilder toReturn = new StringBuilder();
    toReturn.append(JSON_PARSER.writeValueAsString(new SpanLogsDTO(traceId, spanId, spanLogs)));
    toReturn.append("\n");
    return toReturn.toString();
  }
}
