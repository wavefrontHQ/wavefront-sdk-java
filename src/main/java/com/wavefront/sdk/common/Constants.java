package com.wavefront.sdk.common;

/**
 * Class to define all java-sdk constants
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class Constants {

  /**
   * Use this format to send metric data to Wavefront
   */
  public final static String WAVEFRONT_METRIC_FORMAT = "wavefront";

  /**
   * Use this format to send histogram data to Wavefront
   */
  public final static String WAVEFRONT_HISTOGRAM_FORMAT = "histogram";

  /**
   * Use this format to send tracing data to Wavefront
   */
  public final static String WAVEFRONT_TRACING_SPAN_FORMAT = "trace";
}
