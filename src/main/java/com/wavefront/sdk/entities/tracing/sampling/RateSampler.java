package com.wavefront.sdk.entities.tracing.sampling;

/**
 * Sampler that allows a certain probabilistic rate (between 0.0 and 1.0) of spans to be reported.
 * Note: Sampling is performed per trace id. All spans for a sampled trace will be reported.
 *
 * @author Vikram Raman
 */
public class RateSampler implements Sampler {

  private static final double MIN_SAMPLING_RATE = 0.0;
  private static final double MAX_SAMPLING_RATE = 1.0;
  private static final long MOD_FACTOR = 10000L;

  private volatile long boundary;

  /**
   * Constructor.
   *
   * @param samplingRate a sampling rate between 0.0 and 1.0.
   */
  public RateSampler(double samplingRate) {
    setSamplingRate(samplingRate);
  }

  @Override
  public boolean sample(String operationName, long traceId, long duration) {
    return Math.abs(traceId % MOD_FACTOR) <= boundary;
  }

  /**
   * Gets whether a span should be allowed given it's sampling rate and trace id.
   *
   * @param samplingRate The sampling rate between 0.0 and 1.0. of the span
   * @param traceId      The traceId of the span
   * @param duration     The duration of the span in milliseconds
   * @return true if the span should be allowed, false otherwise
   */
  public static boolean sample(double samplingRate, long traceId, long duration) {
    long localBoundary = calculateBoundary(samplingRate);
    return Math.abs(traceId % MOD_FACTOR) <= localBoundary;
  }

  @Override
  public boolean isEarly() {
    return true;
  }

  /**
   * Sets the sampling rate for this sampler.
   *
   * @param samplingRate the sampling rate between 0.0 and 1.0
   */
  public void setSamplingRate(double samplingRate) {
    boundary = calculateBoundary(samplingRate);
  }

  private static long calculateBoundary(double samplingRate) {
    if (samplingRate < MIN_SAMPLING_RATE || samplingRate > MAX_SAMPLING_RATE) {
      throw new IllegalArgumentException("sampling rate must be between " + MIN_SAMPLING_RATE +
              " and " + MAX_SAMPLING_RATE);
    }
    return (long) (samplingRate * MOD_FACTOR);
  }
}
