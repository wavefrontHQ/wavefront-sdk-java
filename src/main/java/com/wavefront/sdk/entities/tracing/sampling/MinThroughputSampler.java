package com.wavefront.sdk.entities.tracing.sampling;

/**
 * Sampler that combines a probabilistic {@link RateSampler} and a {@link MaxThroughputSampler} to enforce
 * a lower bound on the number of traces that are reported.
 *
 * @author Vikram Raman
 */
public class MinThroughputSampler implements Sampler {

    private RateSampler rateSampler;
    private MaxThroughputSampler throughputSampler;

    public MinThroughputSampler(double samplingRate, double tracesPerSecond) {
        rateSampler = new RateSampler(samplingRate);
        throughputSampler = new MaxThroughputSampler(tracesPerSecond);
    }

    @Override
    public boolean sample(String operationName, long traceId, long duration) {
        // call throughput sampler before validating the rateResult to enforce throughput
        boolean rateResult = rateSampler.sample(operationName, traceId, duration);
        boolean throughputResult = throughputSampler.sample(operationName, traceId, duration);
        if (rateResult) {
            return true;
        }
        return throughputResult;
    }

    @Override
    public boolean isEarly() {
        return true;
    }
}
