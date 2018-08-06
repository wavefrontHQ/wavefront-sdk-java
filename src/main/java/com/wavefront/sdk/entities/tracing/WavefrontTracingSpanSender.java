package com.wavefront.sdk.entities.tracing;

import com.wavefront.sdk.common.Pair;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * WavefrontTracingSpanSender interface that sends an open-tracing span to Wavefront
 *
 * @author Vikram Raman (vikram@wavefront.com).
 */
public interface WavefrontTracingSpanSender {

  /**
   * Send a trace span to Wavefront.
   *
   * @param name                The operation name of the span.
   * @param startMillis         The start time in milliseconds for this span.
   * @param durationMicros      The duration of the span in microseconds.
   * @param source              The source (or host) that's sending the span. If null, then
   *                            assigned by Wavefront.
   * @param traceId             The unique trace ID for the span.
   * @param spanId              The unique span ID for the span.
   * @param parents             The list of parent span IDs, can be null if this is a root span.
   * @param followsFrom         The list of preceding span IDs, can be null if this is a root span.
   * @param tags                The span tags associated with this span. Supports repeated tags.
   * @param spanLogs            The span logs associated with this span.
   * @throws IOException        If there was an error sending the span.
   */
  void sendSpan(String name, long startMillis, long durationMicros, @Nullable String source,
                UUID traceId, UUID spanId, @Nullable List<UUID> parents,
                @Nullable List<UUID> followsFrom, @Nullable List<Pair<String, String>> tags,
                @Nullable List<SpanLog> spanLogs)
      throws IOException;
}
