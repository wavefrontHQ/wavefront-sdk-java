package com.wavefront.sdk.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wavefront.sdk.common.annotation.NonNull;
import com.wavefront.sdk.common.annotation.Nullable;
import com.wavefront.sdk.entities.events.EventDTO;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.tracing.SpanLog;
import com.wavefront.sdk.entities.tracing.SpanLogsDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import static com.wavefront.sdk.common.Constants.RESOURCES_ROOT;
import static com.wavefront.sdk.common.Constants.SEMVER_PATTERN;
import static com.wavefront.sdk.common.Constants.SPAN_LOG_KEY;
import static com.wavefront.sdk.common.Constants.VERSION;

/**
 * Common Util methods
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class Utils {

  private static final Logger logger = Logger.getLogger(
          Utils.class.getCanonicalName());

  private static final ObjectMapper JSON_PARSER = new ObjectMapper();

  public static String sanitize(String s) {
    return sanitizeInternal(s, true);
  }

  public static String sanitizeWithoutQuotes(String s) {
    return sanitizeInternal(s, false);
  }

  public static String sanitizeValue(String s) {
    /*
     * Sanitize string of tags value, etc.
     */
    String res = s.trim();
    if (s.contains("\"") || s.contains("'")) {
      // for single quotes, once we are double-quoted, single quotes can exist happily inside it.
      res = res.replace("\"", "\\\"");
    }
    return "\"" + res.replace("\n", "\\n") + "\"";
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

    if (source == null || source.isEmpty()) {
      source = defaultSource;
    }
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("metrics name cannot be blank " +
          getContextInfo(name, source, tags));
    }
    if (source == null || source.isEmpty()) {
      throw new IllegalArgumentException("source cannot be blank " +
          getContextInfo(name, source, tags));
    }

    final StringBuilder sb = new StringBuilder();
    sb.append(sanitize(name));
    sb.append(' ');
    sb.append(value);
    if (timestamp != null) {
      sb.append(' ');
      sb.append(timestamp);
    }
    sb.append(" source=");
    sb.append(sanitizeValue(source));
    if (tags != null) {
      for (final Map.Entry<String, String> tag : tags.entrySet()) {
        String key = tag.getKey();
        String val = tag.getValue();
        if (key == null || key.isEmpty()) {
          throw new IllegalArgumentException("metric point tag key cannot be blank " +
              getContextInfo(name, source, tags));
        }
        if (val == null || val.isEmpty()) {
          throw new IllegalArgumentException("metric point tag value cannot be blank for " +
              "tag key: " + key + " " + getContextInfo(name, source, tags));
        }
        sb.append(' ');
        sb.append(sanitize(key));
        sb.append('=');
        sb.append(sanitizeValue(val));
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

    if (source == null || source.isEmpty()) {
      source = defaultSource;
    }
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("histogram name cannot be blank " +
          getContextInfo(name, source, tags));
    }
    if (source == null || source.isEmpty()) {
      throw new IllegalArgumentException("histogram source cannot be blank " +
          getContextInfo(name, source, tags));
    }
    if (histogramGranularities == null || histogramGranularities.isEmpty()) {
      throw new IllegalArgumentException("Histogram granularities cannot be null or empty " +
          getContextInfo(name, source, tags));
    }
    if (centroids == null || centroids.isEmpty()) {
      throw new IllegalArgumentException("A distribution should have at least one centroid " +
          getContextInfo(name, source, tags));
    }
    final StringBuilder sb = new StringBuilder();
    for (HistogramGranularity histogramGranularity : histogramGranularities) {
      sb.append(histogramGranularity.identifier);
      if (timestamp != null) {
        sb.append(' ');
        sb.append(Long.toString(timestamp));
      }
      sb.append(' ');
      appendCompactedCentroids(sb, centroids);
      sb.append(sanitize(name));
      sb.append(" source=");
      sb.append(sanitizeValue(source));
      if (tags != null) {
        for (final Map.Entry<String, String> tag : tags.entrySet()) {
          String key = tag.getKey();
          String val = tag.getValue();
          if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("histogram tag key cannot be blank " +
                getContextInfo(name, source, tags));
          }
          if (val == null || val.isEmpty()) {
            throw new IllegalArgumentException("histogram tag value cannot be blank for " +
                "tag key: " + key + " " + getContextInfo(name, source, tags));
          }
          sb.append(' ');
          sb.append(sanitize(tag.getKey()));
          sb.append('=');
          sb.append(sanitizeValue(tag.getValue()));
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

    if (source == null || source.isEmpty()) {
      source = defaultSource;
    }
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("span name cannot be blank " +
          getContextInfo(name, source, tags));
    }
    if (source == null || source.isEmpty()) {
      throw new IllegalArgumentException("span source cannot be blank " +
          getContextInfo(name, source, tags));
    }
    final StringBuilder sb = new StringBuilder();
    sb.append(sanitizeValue(name));
    sb.append(" source=");
    sb.append(sanitizeValue(source));
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
          throw new IllegalArgumentException("span tag key cannot be blank " +
              getContextInfo(name, source, tags));
        }
        if (val == null || val.isEmpty()) {
          throw new IllegalArgumentException("span tag value cannot be blank for " +
              "tag key: " + key + " " + getContextInfo(name, source, tags));
        }
        sb.append(' ');
        sb.append(sanitize(key));
        sb.append('=');
        sb.append(sanitizeValue(val));
      }
    }
    if (spanLogs != null && !spanLogs.isEmpty()) {
      sb.append(' ');
      sb.append(sanitize(SPAN_LOG_KEY));
      sb.append('=');
      sb.append(sanitize("true"));
    }
    sb.append(' ');
    sb.append(startMillis);
    sb.append(' ');
    sb.append(durationMillis);
    // TODO - Support SpanLogs
    sb.append('\n');
    return sb.toString();
  }

  public static String eventToLineData(String name, long startMillis, long endMillis,
                                       @Nullable String source, @Nullable Map<String, String> tags,
                                       @Nullable Map<String, String> annotations,
                                       String defaultSource, boolean jsonify)
      throws JsonProcessingException {

    /* Wavefront Event Data format
     *
     * Proxy: @Event <startMillis> <endMillis> <name> [annotations] host=source [tags]
     * Direct Ingestion: JSON string
     *
     * Example:
     * Direct Ingestion:
     *                    {
     *                      "name": "Event Example",
     *                      "hosts": ["localhost"]
     *                      "annotations": {
     *                        "severity": "info",
     *                        "type": "event type",
     *                        "details": "description"
     *                      },
     *                      "tags" : ["key1: value1"],
     *                      "startTime": 1598571847285,
     *                      "endTime": 1598571847286
     *                    }
     * Proxy: @Event 1598571847285 1598571847286 "Event Example" details="description"
     *        type="event type" severity="severity" host="localhost" tag="key1: value1"
     */

    // check and sanitize parameters
    if (source == null || source.isEmpty()) {
      source = defaultSource;
    }
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("event name cannot be blank " +
          getContextInfo(name, source, tags));
    }
    if (source == null || source.isEmpty()) {
      throw new IllegalArgumentException("event source and default source cannot be blank " +
          getContextInfo(name, source, tags));
    }

    if (endMillis == 0) {
      endMillis = startMillis + 1;
    }

    List<String> sanitizedTags = null;
    Map<String, String> sanitizedAnnotations = null;

    if (tags != null) {
      sanitizedTags = new ArrayList<>();
      for (final Map.Entry<String, String> tag : tags.entrySet()) {
        String key = tag.getKey();
        String val = tag.getValue();
        if (key == null || key.isEmpty()) {
          throw new IllegalArgumentException("event tag key cannot be blank " +
              getContextInfo(name, source, tags));
        }
        if (val == null || val.isEmpty()) {
          throw new IllegalArgumentException("event tag value cannot be blank for " +
              "tag key: " + key + " " + getContextInfo(name, source, tags));
        }
        sanitizedTags.add(sanitizeWithoutQuotes(key) + ": " + val.trim());
      }
    }

    if (annotations != null) {
      sanitizedAnnotations = new HashMap<>();
      for (final Map.Entry<String, String> annotation : annotations.entrySet()) {
        String key = annotation.getKey();
        String val = annotation.getValue();
        if (key == null || key.isEmpty()) {
          throw new IllegalArgumentException("event annotation key cannot be blank " +
              getContextInfo(name, source, tags));
        }
        if (val == null || val.isEmpty()) {
          throw new IllegalArgumentException("event annotation value cannot be blank for " +
              "annotation key: " + key + " " + getContextInfo(name, source, tags));
        }
        sanitizedAnnotations.put(sanitizeWithoutQuotes(key), val.trim());
      }
    }

    if (jsonify) {
      // convert event to JSON string
      final StringBuilder toReturn = new StringBuilder();
      // For JSON format in direct ingestion, annotations field is required even it is empty
      if (sanitizedAnnotations == null) {
        sanitizedAnnotations = new HashMap<>();
      }
      toReturn.append(JSON_PARSER.writeValueAsString(new EventDTO(name, startMillis, endMillis,
          source, sanitizedAnnotations, sanitizedTags)))
          .append("\n");
      return toReturn.toString();
    }

    // convert event to line format
    final StringBuilder sb = new StringBuilder();

    sb.append("@Event");
    sb.append(' ');
    sb.append(startMillis);
    sb.append(' ');
    sb.append(endMillis);
    sb.append(' ');
    sb.append(sanitizeValue(name));

    if (sanitizedAnnotations != null) {
      for (final Map.Entry<String, String> annotation : sanitizedAnnotations.entrySet()) {
        sb.append(' ');
        sb.append(annotation.getKey());
        sb.append('=');
        sb.append(sanitizeValue(annotation.getValue()));
      }
    }

    sb.append(" host=");
    sb.append(sanitizeValue(source));

    if (sanitizedTags != null) {
      for (String tag : sanitizedTags) {
        sb.append(" tag=");
        sb.append(sanitizeValue(tag));
      }
    }

    sb.append('\n');
    return sb.toString();
  }

  public static String spanLogsToLineData(UUID traceId, UUID spanId, @NonNull List<SpanLog> spanLogs)
      throws JsonProcessingException {
    return spanLogsToLineData(traceId, spanId, spanLogs, null);
  }

  public static String spanLogsToLineData(UUID traceId, UUID spanId,
                                          @NonNull List<SpanLog> spanLogs, @Nullable String span)
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
    toReturn.append(JSON_PARSER.writeValueAsString(new SpanLogsDTO(traceId, spanId, spanLogs,
        span)));
    toReturn.append("\n");
    return toReturn.toString();
  }

  public static void shutdownExecutorAndWait(ExecutorService tpe) {
    tpe.shutdown();
    try {
      if (!tpe.awaitTermination(60, TimeUnit.SECONDS)) {
        tpe.shutdownNow();
        if (!tpe.awaitTermination(60, TimeUnit.SECONDS))
          logger.log(Level.FINE, "pool did not terminate");
      }
    } catch (InterruptedException ie) {
      tpe.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
  
  private static String sanitizeInternal(String s, boolean addQuotes) {
    /*
     * Sanitize string of metric name, source and key of tags according to the rule of Wavefront proxy.
     */

    StringBuilder sb = new StringBuilder();
    if (addQuotes) {
      sb.append('"');
    }
    for (int i = 0; i < s.length(); i++) {
      char cur = s.charAt(i);
      boolean isLegal = true;
      if (!(44 <= cur && cur <= 57) && !(65 <= cur && cur <= 90) && !(97 <= cur && cur <= 122) &&
          cur != 95) {
        if (!((i == 0 && cur == 0x2206) || (i == 0 && cur == 0x0394) || (i == 0 && cur == 126))) {
          // first character can also be \u2206 (∆ - INCREMENT) or \u0394 (Δ - GREEK CAPITAL LETTER DELTA)
          // or ~ tilda character for internal metrics
          isLegal = false;
        }
      }
      if (cur == '/') {
        isLegal = false;
      }
      sb.append(isLegal ? cur : '-');
    }
    if (addQuotes) {
      sb.append('"');
    }
    return sb.toString();
  }

  /**
   * Builds a best-effort string representation of a metric/histogram/span/event to provide additional
   * context to error messages. This implementation doesn't have to be as strict about escaping
   * values, since this is for internal use only. This method swallows all exceptions so it's
   * safe to use in critical blocks.
   *
   * @param name   Entity name
   * @param source Source name
   * @param tags   A collection of tags, either as {@code List<Pair<String, String>>} or
   *               {@code Map<String, String>}.
   * @return best-effort string representation or an empty string if it's not possible
   */
  @SuppressWarnings("unchecked")
  private static String getContextInfo(@Nullable String name, @Nullable String source,
                                       @Nullable Object tags) {
    try {
      StringBuilder sb = new StringBuilder("(");
      if (name != null) sb.append(name);
      if (source != null) sb.append(" source=").append(source);
      if (tags != null) {
        if (tags instanceof Map) {
          for (Map.Entry<String, String> entry : ((Map<String, String>) tags).entrySet()) {
            sb.append(' ').append(entry.getKey()).append("=[").append(entry.getValue()).append(']');
          }
        } else if (tags instanceof List) {
          for (Pair<String, String> entry : (List<Pair<String, String>>) tags) {
            sb.append(' ').append(entry._1).append("=[").append(entry._2).append(']');
          }
        }
      }
      sb.append(")");
      return sb.toString();
    } catch (Exception ex) {
      return "";
    }
  }

  private static void appendCompactedCentroids(StringBuilder sb,
                                               List<Pair<Double, Integer>> centroids) {
    Double accumulatedValue = null;
    int accumulatedCount = 0;
    for (Pair<Double, Integer> centroid : centroids) {
      double value = centroid._1;
      int count = centroid._2;
      if (accumulatedValue != null && value != accumulatedValue) {
        sb.append('#').append(accumulatedCount).append(' ');
        sb.append(accumulatedValue).append(' ');
        accumulatedValue = value;
        accumulatedCount = count;
      } else {
        if (accumulatedValue == null) {
          accumulatedValue = value;
        }
        accumulatedCount += count;
      }
    }
    if (accumulatedValue != null) {
      sb.append('#').append(accumulatedCount).append(' ');
      sb.append(accumulatedValue).append(' ');
    }
  }

  /**
   * Return the resource at the given resource path in the resource directory
   * {@code META-INF/}.
   *
   * @param pathToResource
   * @return the resource at the given path, if exists.
   */
  public static Optional<ResourceBundle> getResource(String pathToResource) {
    if (pathToResource != null && !pathToResource.isEmpty()) {
      ResourceBundle resourceBundle = null;
      try {
        resourceBundle = ResourceBundle.getBundle(RESOURCES_ROOT + pathToResource);
      } catch (Exception ex) {
        logger.log(Level.WARNING, ex.getMessage());
      }
      if (resourceBundle != null) {
        return Optional.of(resourceBundle);
      }
    }
    return Optional.empty();
  }

  /**
   * Return the version of the given artifact Id.
   * This depends on a file with name "&lt;artifactId&gt;.properties" in the resource directory
   * {@code META-INF/}. The file should contain a line that specifies the project version as
   * "project.version".
   *
   * @param artifactId
   * @return the version for the given artifactId
   */
  public static Optional<String> getVersion(String artifactId) {
    if (artifactId != null && !artifactId.isEmpty()) {
      Optional<ResourceBundle> resource = getResource(artifactId + "/build");
      if (resource.isPresent()) {
        ResourceBundle resourceBundle = resource.get();
        if (resourceBundle.containsKey(VERSION)) {
          String version = resourceBundle.getString(VERSION);
          if (version != null && !version.isEmpty()) {
            return Optional.of(version);
          }
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Return the version of the given artifact Id as a gauge value to be reported to Wavefront.
   * This depends on a file with name "&lt;artifactId&gt;.properties" in the resource directory
   * {@code META-INF/}. The file should contain a line that specifies the project version as
   * "project.version".
   * Format of semantic version gauge value reported to Wavefront =
   * <majorVersion>.<2-digit-minorVersion><2-digit-patchVersion>
   *
   * Ex: v2.6.1 => 2.0601
   *
   * @param artifactId
   * @return the version Gauge value for the given artifactId
   */
  public static double getSemVerGauge(String artifactId) {
    Optional<String> version = getVersion(artifactId);
    if (version.isPresent()) {
      return convertSemVerToGauge(version.get());
    } else {
      logger.log(Level.INFO, "Could not retrieve build version info for : ", artifactId);
    }
    return 0.0D;
  }

  public static double convertSemVerToGauge(String version) {
    if (version != null && !version.isEmpty()) {
      Matcher semVerMatcher = SEMVER_PATTERN.matcher(version);
      if (semVerMatcher.matches()) {
        // Major version
        StringBuilder sdkVersion = new StringBuilder(semVerMatcher.group(1));
        sdkVersion.append(".");
        String minor = semVerMatcher.group(2);
        String patch = semVerMatcher.group(3);
        String snapshot = semVerMatcher.group(4);
        // Minor version
        if (minor.length() == 1) {
          sdkVersion.append("0" + minor);
        } else {
          sdkVersion.append(minor);
        }
        // Patch Version
        if (patch.length() == 1) {
          sdkVersion.append("0" + patch);
        } else {
          sdkVersion.append(patch);
        }
        try {
          return Double.valueOf(sdkVersion.toString());
        } catch (Exception ex) {
          logger.log(Level.INFO, ex.getMessage());
        }
      }
    }
    return 0.0D;
  }
}
