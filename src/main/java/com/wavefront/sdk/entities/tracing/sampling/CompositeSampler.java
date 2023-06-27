package com.wavefront.sdk.entities.tracing.sampling;

import java.util.List;

/**
 * Sampler that delegates to multiple other samplers for sampling.
 * The sampling decision is true if any of the delegate samplers allow the span.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 * @version $Id: $Id
 */
public class CompositeSampler implements Sampler {

  private final List<Sampler> samplers;

  /**
   * <p>Constructor for CompositeSampler.</p>
   *
   * @param samplers a {@link java.util.List} object
   */
  public CompositeSampler(List<Sampler> samplers) {
    this.samplers = samplers;
  }

  /** {@inheritDoc} */
  @Override
  public boolean sample(String operationName, long traceId, long duration) {
    if (samplers == null || samplers.isEmpty()) {
      return true;
    }
    for (Sampler sampler : samplers) {
      if (sampler.sample(operationName, traceId, duration)) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isEarly() {
    return false;
  }
}
