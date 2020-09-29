package com.wavefront.sdk.common.metrics;


import com.wavefront.sdk.common.Constants;

/**
 * A delta counter used for metrics that are internal to Wavefront SDKs. Delta Counters measures change since metric
 * was last recorded, and is prefixed with ∆.
 *
 * @author Joanna Ko (joannak@vmware.com).
 */
public class WavefrontSdkDeltaCounter extends WavefrontSdkCounter {

}


