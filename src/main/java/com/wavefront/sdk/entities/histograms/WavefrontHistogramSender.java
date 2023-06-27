package com.wavefront.sdk.entities.histograms;

import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.annotation.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WavefrontHistogramSender interface that sends a distribution to Wavefront
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 * @version $Id: $Id
 */
public interface WavefrontHistogramSender {

  /**
   * <p>sendDistribution.</p>
   *
   * @param name                       The name of the histogram distribution. Spaces are replaced
   *                                   with '-' (dashes) and quotes will be automatically escaped.
   * @param centroids                  The distribution of histogram points to be sent.
   *                                   Each centroid is a 2-dimensional {@link com.wavefront.sdk.common.Pair} where the
   *                                   first dimension is the mean value (Double) of the centroid
   *                                   and second dimension is the count of points in that centroid.
   * @param histogramGranularities     The set of intervals (minute, hour, and/or day) by which
   *                                   histogram data should be aggregated.
   * @param timestamp                  The timestamp in milliseconds since the epoch to be sent.
   *                                   If null then the timestamp is assigned by Wavefront when
   *                                   data is received.
   * @param source                     The source (or host) that's sending the histogram. If
   *                                   null, then assigned by Wavefront.
   * @param tags                       The tags associated with this histogram.
   * @throws java.io.IOException               If there was an error sending the histogram.
   */
  void sendDistribution(String name, List<Pair<Double, Integer>> centroids,
                        Set<HistogramGranularity> histogramGranularities, @Nullable Long timestamp,
                        @Nullable String source, @Nullable Map<String, String> tags)
      throws IOException;
}
