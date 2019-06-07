package com.wavefront.sdk.entities.tracing.sampling;

import com.wavefront.sdk.common.RateLimiter;

/**
 * Sampler that allows a fixed number of traces to be reported per time interval.
 *
 * For example: 1000 traces per second or 20 traces per minute etc.
 *
 * @author Vikram Raman
 */
public class MaxThroughputSampler implements Sampler {

    private final RateLimiter rateLimiter;

    /**
     * Constructor.
     *
     * @param tracesPerSecond The maximum number of traces to be sampled per second.
     *                        Fractional values are supported.
     */
    public MaxThroughputSampler(double tracesPerSecond) {
        this.rateLimiter = new RateLimiter(tracesPerSecond, 0.1);
    }

    @Override
    public boolean sample(String operationName, long traceId, long duration) {
        return rateLimiter.isPermitted(1.0);
    }

    @Override
    public boolean isEarly() {
        return true;
    }
}
