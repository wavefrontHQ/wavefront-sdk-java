package com.wavefront.sdk.entities.tracing;

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

  public SpanLogsDTO(UUID traceId, UUID spanId, List<SpanLog> spanLogs) {
    this.traceId = traceId;
    this.spanId = spanId;
    this.logs = spanLogs;
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
}
