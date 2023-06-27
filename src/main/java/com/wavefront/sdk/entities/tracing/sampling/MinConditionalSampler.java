package com.wavefront.sdk.entities.tracing.sampling;

import java.util.List;

/**
 * This an extension of {@link com.wavefront.sdk.entities.tracing.sampling.CompositeSampler} that runs a pre-check to determine if a span is
 * eligible for sampling. This implementation needs span to meet the minimum duration threshold
 * to be considered for sampling, otherwise span is discarded.
 *
 * @author Anil Kodali (akodali@vmware.com)
 * @version $Id: $Id
 */
public class MinConditionalSampler extends CompositeSampler{

  private volatile long minimumDurationMillis;

  /**
   * Constructor.
   *
   * @param samplers              The list of samplers to delegate to if the minimum duration
   *                              condition is met.
   * @param minimumDurationMillis The minimum duration in milliseconds. Spans with durations lesser
   *                              than this are discarded.
   */
  public MinConditionalSampler(List<Sampler> samplers, long minimumDurationMillis) {
    super(samplers);
    this.minimumDurationMillis = minimumDurationMillis;
  }

  /** {@inheritDoc} */
  @Override
  public boolean sample(String operationName, long traceId, long duration) {
    if (duration < this.minimumDurationMillis) {
      return false;
    }
    return super.sample(operationName, traceId, duration);
  }

  /**
   * Sets the minimum duration for this sampler.
   *
   * @param minimumDurationMillis The minimum duration in milliseconds.
   */
  public void setMinimumDurationMillis(long minimumDurationMillis) {
    this.minimumDurationMillis = minimumDurationMillis;
  }
}
