package com.wavefront.sdk.entities.tracing.sampling;

import java.util.List;

/**
 * This an extension of {@link CompositeSampler} that runs a pre-check to determine if a span is
 * eligible for sampling. This implementation needs span to meet the minimum duration threshold
 * to be considered for sampling, otherwise span is discarded.
 *
 * @author Anil Kodali (akodali@vmware.com)
 */
public class MinConditionalSampler extends CompositeSampler{

  private final long minimumDuration;

  public MinConditionalSampler(List<Sampler> samplers, long minimumDuration) {
    super(samplers);
    this.minimumDuration = minimumDuration;
  }

  @Override
  public boolean sample(String operationName, long traceId, long duration) {
    if (duration < this.minimumDuration) {
      return false;
    }
    return super.sample(operationName, traceId, duration);
  }
}
