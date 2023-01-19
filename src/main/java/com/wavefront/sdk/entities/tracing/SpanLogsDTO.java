package com.wavefront.sdk.entities.tracing;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.wavefront.sdk.common.annotation.Nullable;

import java.util.List;
import java.util.UUID;

import static com.wavefront.sdk.common.Constants.SPAN_SECONDARY_ID_KEY;

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

  @Nullable
  private final String spanSecondaryId;

  public SpanLogsDTO(UUID traceId, UUID spanId, List<SpanLog> spanLogs) {
    this(traceId, spanId, spanLogs, null);
  }

  public SpanLogsDTO(UUID traceId, UUID spanId, List<SpanLog> spanLogs, @Nullable String span) {
    this(traceId, spanId, spanLogs, span, null);
  }

  public SpanLogsDTO(
          UUID traceId, UUID spanId, List<SpanLog> spanLogs, @Nullable String span,
          @Nullable String spanSecondaryId) {
    this.traceId = traceId;
    this.spanId = spanId;
    this.logs = spanLogs;
    this.span = span;
    this.spanSecondaryId = spanSecondaryId;
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

  @JsonGetter(SPAN_SECONDARY_ID_KEY)
  public String getSpanSecondaryId() {
    return spanSecondaryId;
  }
}
