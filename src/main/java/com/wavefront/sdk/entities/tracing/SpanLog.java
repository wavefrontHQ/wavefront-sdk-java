package com.wavefront.sdk.entities.tracing;

import java.util.Map;

/**
 * SpanLog defined as per the opentracing.io specification
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class SpanLog {
  private final long timestamp;
  private final Map<String, String> fields;

  public SpanLog(long timestamp, Map<String, String> fields) {
    this.timestamp = timestamp;
    this.fields = fields;
  }
}
