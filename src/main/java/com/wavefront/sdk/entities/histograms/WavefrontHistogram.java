package com.wavefront.sdk.entities.histograms;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.tdunning.math.stats.AVLTreeDigest;
import com.tdunning.math.stats.Centroid;
import com.tdunning.math.stats.TDigest;
import com.wavefront.sdk.common.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.lang.Double.NaN;

/**
 * Wavefront implementation of a histogram
 *
 * @author Han Zhang (zhanghan@vmware.com)
 */
public class WavefrontHistogram {
  private final static int ACCURACY = 100;

  /**
   * If a thread's bin queue has exceeded MAX_BINS number of bins (e.g., the thread has data
   * that has yet to be reported for more than MAX_BINS number of minutes), delete the oldest bin.
   * Defaulted to 10 because we can expect the histogram to be reported at least once every 10 minutes.
   */
  private final static int MAX_BINS = 10;

  private final Supplier<Long> clockMillis;

  /**
   * Cache of thread ids to bin queues. By giving each thread its own bin queue, we can ensure
   * thread safety by locking only the relevant bin queue for a particular thread. This is more performant
   * than locking the entire histogram.
   * <p>
   * Entries are automatically removed if they aren't accessed for 1 hour, which serves to remove empty bin queues
   * for threads that are no longer reporting distributions.
   */
  private final LoadingCache<Long, LinkedList<MinuteBin>> perThreadHistogramBins = Caffeine.newBuilder().
      expireAfterAccess(1, TimeUnit.HOURS).
      build(key -> new LinkedList<>());

  public WavefrontHistogram() {
    this(System::currentTimeMillis);
  }

  public WavefrontHistogram(Supplier<Long> clockMillis) {
    this.clockMillis = clockMillis;
  }

  public void update(int value) {
    update((double) value);
  }

  public void update(long value) {
    update((double) value);
  }

  public void update(double value) {
    getCurrentBin().distribution.add(value);
  }

  /**
   * Bulk-update this histogram with a set of centroids.
   *
   * @param means  the centroid values
   * @param counts the centroid weights/sample counts
   */
  public void bulkUpdate(List<Double> means, List<Integer> counts) {
    if (means != null && counts != null) {
      int n = Math.min(means.size(), counts.size());
      MinuteBin currentBin = getCurrentBin();
      for (int i = 0; i < n; ++i) {
        currentBin.distribution.add(means.get(i), counts.get(i));
      }
    }
  }

  /**
   * @return returns the number of values in the distribution.
   */
  public long getCount() {
    return perThreadHistogramBins.asMap().values().stream().flatMap(List::stream).
        mapToLong(bin -> bin.distribution.size()).sum();
  }

  /**
   * @return returns the maximum value in the distribution. Returns NaN if the distribution is empty.
   */
  public double getMax() {
    return perThreadHistogramBins.asMap().values().stream().flatMap(List::stream).
        mapToDouble(bin -> bin.distribution.getMax()).max().orElse(NaN);
  }

  /**
   * @return returns the minimum value in the distribution. Returns NaN if the distribution is empty.
   */
  public double getMin() {
    return perThreadHistogramBins.asMap().values().stream().flatMap(List::stream).
        mapToDouble(bin -> bin.distribution.getMin()).min().orElse(NaN);
  }

  /**
   * @return returns the mean of the values in the distribution. Returns NaN if the distribution is empty.
   */
  public double getMean() {
    List<Centroid> centroids = new ArrayList<>();
    perThreadHistogramBins.asMap().values().stream().flatMap(List::stream).
        forEach(bin -> centroids.addAll(bin.distribution.centroids()));

    return centroids.size() == 0 ?
        NaN :
        centroids.stream().mapToDouble(c -> (c.count() * c.mean()) / centroids.size()).sum();
  }

  /**
   * @return returns the sum of the values in the distribution.
   */
  public double getSum() {
    List<Centroid> centroids = new ArrayList<>();
    perThreadHistogramBins.asMap().values().stream().flatMap(List::stream).
        forEach(bin -> centroids.addAll(bin.distribution.centroids()));

    return centroids.stream().mapToDouble(c -> c.count() * c.mean()).sum();
  }

  /**
   * Not supported, hence return Double.NaN
   *
   * @return stdDev
   */
  public double stdDev() {
    return Double.NaN;
  }

  /**
   * Aggregates all the minute bins prior to the current minute (because threads might be updating the current minute
   * bin while the method is invoked) and returns a list of the distributions held within each bin. Note that invoking
   * this method will also clear all data from the aggregated bins, thereby changing the state of the system and
   * preventing data from being flushed more than once.
   *
   * @return returns a list of distributions, each a {@link Distribution} holding a timestamp as well as a list of
   * centroids. Each centroid is a tuple containing the centroid value and count.
   */
  public List<Distribution> flushDistributions() {
    final long cutoffMillis = currentMinuteMillis();
    final List<MinuteBin> minuteBins = new ArrayList<>();

    perThreadHistogramBins.asMap().values().stream().flatMap(List::stream).
        filter(bin -> bin.minuteMillis < cutoffMillis).forEach(minuteBins::add);

    final List<Distribution> distributions = new ArrayList<>();
    for (MinuteBin minuteBin : minuteBins) {
      List<Pair<Double, Integer>> centroids = minuteBin.distribution.centroids().stream().
          map(c -> new Pair<>(c.mean(), c.count())).collect(Collectors.toList());
      distributions.add(new Distribution(minuteBin.minuteMillis, centroids));
    }

    clearPriorCurrentMinuteBin(cutoffMillis);

    return distributions;
  }

  /**
   * @return returns a statistical {@link Snapshot} of the histogram distribution.
   */
  public Snapshot getSnapshot() {
    final TDigest snapshot = new AVLTreeDigest(ACCURACY);
    perThreadHistogramBins.asMap().values().stream().flatMap(List::stream).
        forEach(bin -> snapshot.add(bin.distribution));
    return new Snapshot(snapshot);
  }
  // TODO - how to ensure thread safety? do we care?

  private long currentMinuteMillis() {
    return (clockMillis.get() / 60000L) * 60000L;
  }

  /**
   * Helper to retrieve the current bin. Will be invoked per thread.
   */
  private MinuteBin getCurrentBin() {
    long key = Thread.currentThread().getId();
    LinkedList<MinuteBin> bins = perThreadHistogramBins.get(key);
    long currMinuteMillis = currentMinuteMillis();

    // bins with clear == true flag will drain (CONSUMER) the list,
    // so synchronize the access to the respective 'bins' list
    synchronized (bins) {
      if (bins.isEmpty() || bins.getLast().minuteMillis != currMinuteMillis) {
        bins.add(new MinuteBin(ACCURACY, currMinuteMillis));
        if (bins.size() > MAX_BINS) {
          bins.removeFirst();
        }
      }
      return bins.getLast();
    }
  }

  private void clearPriorCurrentMinuteBin(long cutoffMillis) {
    for (LinkedList<MinuteBin> bins : perThreadHistogramBins.asMap().values()) {
      // getCurrentBin() method will add (PRODUCER) item to the bins list, so synchronize the access
      synchronized (bins) {
        bins.removeIf(minuteBin -> minuteBin.minuteMillis < cutoffMillis);
      }
    }
  }

  /**
   * Wrapper for TDigest distribution
   */
  public static class Snapshot {
    private final TDigest distribution;

    Snapshot(TDigest dist) {
      this.distribution = dist;
    }

    /**
     * @return returns the number of values in the distribution.
     */
    public long getCount() {
      return distribution.size();
    }

    /**
     * @return returns the maximum value in the distribution. Returns NaN if the distribution is empty.
     */
    public double getMax() {
      double max = distribution.getMax();
      return max == Double.NEGATIVE_INFINITY ? NaN : max;
    }

    /**
     * @return returns the minimum value in the distribution. Returns NaN if the distribution is empty.
     */
    public double getMin() {
      double min = distribution.getMin();
      return min == Double.POSITIVE_INFINITY ? NaN : min;
    }

    /**
     * @return returns the mean of the values in the distribution. Returns NaN if the distribution is empty.
     */
    public double getMean() {
      Collection<Centroid> centroids = distribution.centroids();
      return centroids.size() == 0 ?
          NaN :
          centroids.stream().mapToDouble(c -> (c.count() * c.mean()) / centroids.size()).sum();
    }

    /**
     * @return returns the sum of the values in the distribution.
     */
    public double getSum() {
      return distribution.centroids().stream().mapToDouble(c -> c.count() * c.mean()).sum();
    }

    /**
     * @param quantile  a given quantile, between 0 and 1
     * @return returns the value in the distribution at the given quantile. Returns NaN if the distribution is empty.
     */
    public double getValue(double quantile) {
      return distribution.quantile(quantile);
    }

    /**
     * Returns the size of the snapshot
     *
     * @return size of the snapshot
     */
    public int getSize() {
      return (int)distribution.size();
    }
  }

  /**
   * Representation of a histogram distribution, containing a timestamp and a list of centroids.
   */
  public static class Distribution {
    /**
     * The timestamp in milliseconds since the epoch.
     */
    public final long timestamp;

    /**
     * The list of histogram points, each a 2-dimensional {@link Pair} where the first dimension is the mean value
     * (Double) of the centroid and second dimension is the count of points in that centroid.
     */
    public final List<Pair<Double, Integer>> centroids;

    public Distribution(long timestamp, List<Pair<Double, Integer>> centroids) {
      this.timestamp = timestamp;
      this.centroids = centroids;
    }
  }

  /**
   * Representation of a bin that holds histogram data for a particular minute in time.
   */
  private static class MinuteBin {
    /**
     * The histogram data for the minute bin, represented as a {@link TDigest} distribution
     */
    public final TDigest distribution;

    /**
     * The timestamp at the start of the minute.
     */
    public final long minuteMillis;

    MinuteBin(int accuracy, long minuteMillis) {
      distribution = new AVLTreeDigest(accuracy);
      this.minuteMillis = minuteMillis;
    }
  }
}
