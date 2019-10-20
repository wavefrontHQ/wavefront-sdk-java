package com.wavefront.sdk.entities.tracing.sampling;

/**
 * The interface for sampling tracing spans.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public interface Sampler {

  /**
   * Gets whether a span should be allowed given it's operation and trace id.
   *
   * @param operationName The operation name of the span
   * @param traceId The traceId of the span
   * @param duration The duration of the span in milliseconds
   * @return true if the span should be allowed, false otherwise
   */
  boolean sample(String operationName, long traceId, long duration);

  /**
   * Whether this sampler performs early or head based sampling.
   * Offers a non-binding hint for clients using the sampler.
   *
   * @return true for early sampling, false otherwise
   */
  boolean isEarly();
}
