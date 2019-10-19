package com.wavefront.sdk.entities.tracing.sampling;

/**
 * Sampler that allows a certain probabilistic rate (between 0.0 and 1.0) of spans to be reported.
 * <p>
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

    @Override
    public boolean sample(double samplingRate, long traceId, long duration) {
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

    private long calculateBoundary(double samplingRate) {
        if (samplingRate < MIN_SAMPLING_RATE || samplingRate > MAX_SAMPLING_RATE) {
            throw new IllegalArgumentException("sampling rate must be between " + MIN_SAMPLING_RATE +
                    " and " + MAX_SAMPLING_RATE);
        }
        return (long) (samplingRate * MOD_FACTOR);
    }
}
