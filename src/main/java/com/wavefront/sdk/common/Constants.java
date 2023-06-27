package com.wavefront.sdk.common;

import java.util.regex.Pattern;

/**
 * Class to define all java-sdk constants
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 * @version $Id: $Id
 */
public final class Constants {

  private Constants() {
  }

  /**
   * Use this format to send metric data to Wavefront
   */
  public static final String WAVEFRONT_METRIC_FORMAT = "wavefront";

  /**
   * Use this format to send histogram data to Wavefront
   */
  public static final String WAVEFRONT_HISTOGRAM_FORMAT = "histogram";

  /**
   * Use this format to send log data to Wavefront
   */
  public static final String WAVEFRONT_LOG_FORMAT = "log";

  /**
   * Use this format to send tracing data to Wavefront
   */
  public static final String WAVEFRONT_TRACING_SPAN_FORMAT = "trace";

  /**
   * Use this format to send tracing span log data to Wavefront
   */
  public static final String WAVEFRONT_SPAN_LOG_FORMAT = "spanLogs";

  /**
   * Use this format to send event data to Wavefront
   */
  public static final String WAVEFRONT_EVENT_FORMAT = "event";

  /**
   * URI scheme for proxy
   */
  public static final String HTTP_PROXY_SCHEME = "http";

  /**
   * ∆: INCREMENT
   */
  public static final String DELTA_PREFIX = "\u2206";

  /**
   * Δ: GREEK CAPITAL LETTER DELTA
   */
  public static final String DELTA_PREFIX_2 = "\u0394";

  /**
   * Heartbeat metric
   */
  public static final String HEART_BEAT_METRIC = "~component.heartbeat";

  /**
   * Internal source used for internal and aggregated metrics
   */
  public static final String WAVEFRONT_PROVIDED_SOURCE = "wavefront-provided";

  /**
   * Null value emitted for optional undefined tags.
   */
  public static final String NULL_TAG_VAL = "none";

  /**
   * Key for defining a source.
   */
  public static final String SOURCE_KEY = "source";

  /**
   * Tag key for defining an application.
   */
  public static final String APPLICATION_TAG_KEY = "application";

  /**
   * Tag key for defining a cluster.
   */
  public static final String CLUSTER_TAG_KEY = "cluster";

  /**
   * Tag key for defining an origin.
   */
  public static final String ORIGIN_TAG_KEY = "origin";

  /**
   * Tag key for defining a shard.
   */
  public static final String SHARD_TAG_KEY = "shard";

  /**
   * Tag key  for defining a service.
   */
  public static final String SERVICE_TAG_KEY = "service";

  /**
   * Tag key for defining a component.
   */
  public static final String COMPONENT_TAG_KEY = "component";

  /**
   * Tag key for indicating span logs are present for a span.
   */
  public static final String SPAN_LOG_KEY = "_spanLogs";

  /**
   * Tag key to uniquely identify an OpenZipkin Brave span when combined with Span Id. The tag value
   * should be the span's kind (e.g. "client", "server", "producer", "consumer"). Should only be
   * needed in scenarios where Span Id isn't guaranteed to be unique within a Trace (only known
   * case is OpenZipkin Brave).
   */
  public static final String SPAN_SECONDARY_ID_KEY = "_spanSecondaryId";

  /**
   * Tag key for defining a process identifier.
   */
  public static final String PROCESS_TAG_KEY = "pid";

  /**
   * Tag key for defining an instance identifier.
   */
  public static final String INSTANCE_TAG_KEY = "instanceId";

  /**
   * Tag key for defining an error span.
   */
  public static final String ERROR_TAG_KEY = "error";

  /**
   * Tag key for defining an debug span.
   */
  public static final String DEBUG_TAG_KEY = "debug";

  /**
   * Name prefix for internal diagnostic metrics for Wavefront SDKs.
   */
  public static final String SDK_METRIC_PREFIX = "~sdk.java";

  /**
   * Semantic version pattern matcher regex.
   */
  public static final Pattern SEMVER_PATTERN = Pattern.
      compile("([0-9]\\d*)\\.(\\d+)\\.(\\d+)(?:-([a-zA-Z0-9]+))?");

  /**
   * The Project Version of the sdk or artifactId in properties file.
   */
  public static final String VERSION = "project.version";


  /**
   * The root folder of the resources for a artifactId.
   */
  public static final String RESOURCES_ROOT = "META-INF/";

}
