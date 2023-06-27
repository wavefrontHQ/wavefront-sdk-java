package com.wavefront.sdk.entities.tracing;

import java.util.Map;

/**
 * SpanLog defined as per the opentracing.io specification
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 * @version $Id: $Id
 */
public class SpanLog {
  // expected to be in micros
  private final long timestamp;
  private final Map<String, String> fields;

  /**
   * <p>Constructor for SpanLog.</p>
   *
   * @param timestamp a long
   * @param fields a {@link java.util.Map} object
   */
  public SpanLog(long timestamp, Map<String, String> fields) {
    this.timestamp = timestamp;
    this.fields = fields;
  }

  /**
   * <p>Getter for the field <code>timestamp</code>.</p>
   *
   * @return a long
   */
  public long getTimestamp() {
    return timestamp;
  }

  /**
   * <p>Getter for the field <code>fields</code>.</p>
   *
   * @return a {@link java.util.Map} object
   */
  public Map<String, String> getFields() {
    return fields;
  }
}
