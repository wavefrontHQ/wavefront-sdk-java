package com.wavefront.sdk.common.metrics;

import com.wavefront.sdk.common.Constants;

/**
 * A delta counter used for metrics that are internal to Wavefront SDKs. Delta Counters measures change since metric
 * was last recorded, and is prefixed with \u2206 (∆ - INCREMENT) or \u0394 (Δ - GREEK CAPITAL LETTER DELTA).
 *
 * @author Joanna Ko (joannak@vmware.com).
 */
public class WavefrontSdkDeltaCounter extends WavefrontSdkCounter {
}
