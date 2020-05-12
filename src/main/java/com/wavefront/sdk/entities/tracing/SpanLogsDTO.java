package com.wavefront.sdk.entities.tracing;

import com.wavefront.sdk.common.annotation.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * DTO for the spanLogs to be sent to Wavefront.
 *
 * @author Srujan Narkedamalli (snarkedamall@wavefront.com).
 */
public class SpanLogsDTO {
  private final UUID traceId;
  private final UUID spanId;
  private final List<SpanLog> logs;

  // For sampling. If null, span log will always be sampled (i.e., retained).
  @Nullable
  private final String span;

  public SpanLogsDTO(UUID traceId, UUID spanId, List<SpanLog> spanLogs) {
    this(traceId, spanId, spanLogs, null);
  }

  public SpanLogsDTO(UUID traceId, UUID spanId, List<SpanLog> spanLogs, @Nullable String span) {
    this.traceId = traceId;
    this.spanId = spanId;
    this.logs = spanLogs;
    this.span = span;
  }

  public UUID getTraceId() {
    return traceId;
  }

  public UUID getSpanId() {
    return spanId;
  }

  public List<SpanLog> getLogs() {
    return logs;
  }

  public String getSpan() {
    return span;
  }
}
