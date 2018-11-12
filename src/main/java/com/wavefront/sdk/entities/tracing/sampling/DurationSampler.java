package com.wavefront.sdk.entities.tracing.sampling;

/**
 * Sampler that allows spans above a given duration in milliseconds to be reported.
 *
 * @author Vikram Raman
 */
public class DurationSampler implements Sampler {

  private final long duration;

  /**
   * Constructor.
   *
   * @param duration The duration in milliseconds. Spans with durations higher than this are reported.
   */
  public DurationSampler(long duration) {
    this.duration = duration;
  }

  @Override
  public boolean sample(String operationName, long traceId, long duration) {
    return duration > this.duration;
  }

  @Override
  public boolean isEarly() {
    return false;
  }
}
