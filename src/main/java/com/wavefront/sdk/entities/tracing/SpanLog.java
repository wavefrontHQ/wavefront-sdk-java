package com.wavefront.sdk.entities.tracing;

import java.util.Map;

/**
 * SpanLog defined as per the opentracing.io specification
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class SpanLog {
  // expected to be in micros
  private final long timestamp;
  private final Map<String, String> fields;

  public SpanLog(long timestamp, Map<String, String> fields) {
    this.timestamp = timestamp;
    this.fields = fields;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public Map<String, String> getFields() {
    return fields;
  }
}
