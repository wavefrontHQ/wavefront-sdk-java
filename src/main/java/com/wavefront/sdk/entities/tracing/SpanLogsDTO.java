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
 * @version $Id: $Id
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

  /**
   * <p>Constructor for SpanLogsDTO.</p>
   *
   * @param traceId a {@link java.util.UUID} object
   * @param spanId a {@link java.util.UUID} object
   * @param spanLogs a {@link java.util.List} object
   */
  public SpanLogsDTO(UUID traceId, UUID spanId, List<SpanLog> spanLogs) {
    this(traceId, spanId, spanLogs, null);
  }

  /**
   * <p>Constructor for SpanLogsDTO.</p>
   *
   * @param traceId a {@link java.util.UUID} object
   * @param spanId a {@link java.util.UUID} object
   * @param spanLogs a {@link java.util.List} object
   * @param span a {@link java.lang.String} object
   */
  public SpanLogsDTO(UUID traceId, UUID spanId, List<SpanLog> spanLogs, @Nullable String span) {
    this(traceId, spanId, spanLogs, span, null);
  }

  /**
   * <p>Constructor for SpanLogsDTO.</p>
   *
   * @param traceId a {@link java.util.UUID} object
   * @param spanId a {@link java.util.UUID} object
   * @param spanLogs a {@link java.util.List} object
   * @param span a {@link java.lang.String} object
   * @param spanSecondaryId a {@link java.lang.String} object
   */
  public SpanLogsDTO(
          UUID traceId, UUID spanId, List<SpanLog> spanLogs, @Nullable String span,
          @Nullable String spanSecondaryId) {
    this.traceId = traceId;
    this.spanId = spanId;
    this.logs = spanLogs;
    this.span = span;
    this.spanSecondaryId = spanSecondaryId;
  }

  /**
   * <p>Getter for the field <code>traceId</code>.</p>
   *
   * @return a {@link java.util.UUID} object
   */
  public UUID getTraceId() {
    return traceId;
  }

  /**
   * <p>Getter for the field <code>spanId</code>.</p>
   *
   * @return a {@link java.util.UUID} object
   */
  public UUID getSpanId() {
    return spanId;
  }

  /**
   * <p>Getter for the field <code>logs</code>.</p>
   *
   * @return a {@link java.util.List} object
   */
  public List<SpanLog> getLogs() {
    return logs;
  }

  /**
   * <p>Getter for the field <code>span</code>.</p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getSpan() {
    return span;
  }

  /**
   * <p>Getter for the field <code>spanSecondaryId</code>.</p>
   *
   * @return a {@link java.lang.String} object
   */
  @JsonGetter(SPAN_SECONDARY_ID_KEY)
  public String getSpanSecondaryId() {
    return spanSecondaryId;
  }
}
