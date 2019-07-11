package com.wavefront.sdk.common;

import com.wavefront.sdk.entities.histograms.WavefrontHistogramSender;
import com.wavefront.sdk.entities.metrics.WavefrontMetricSender;
import com.wavefront.sdk.entities.tracing.WavefrontTracingSpanSender;

import java.io.Closeable;

/**
 * An uber WavefrontSender that abstracts various atom senders along with flushing and closing logic
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public interface WavefrontSender extends WavefrontMetricSender, WavefrontHistogramSender,
    WavefrontTracingSpanSender, BufferFlusher, Closeable {
    String getClientId();
}
