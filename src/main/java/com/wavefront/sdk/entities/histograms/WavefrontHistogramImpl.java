package com.wavefront.sdk.entities.histograms;

import com.tdunning.math.stats.AVLTreeDigest;
import com.tdunning.math.stats.Centroid;
import com.tdunning.math.stats.TDigest;
import com.wavefront.sdk.common.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.lang.Double.NaN;

/**
 * Wavefront implementation of a histogram
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class WavefrontHistogramImpl {
  /**
   * We support approx 100 centroids for every minute bin T-Digest distributions
   */
  private final static int ACCURACY = 100;

  /**
   * If a thread's bin queue has exceeded MAX_BINS number of bins (e.g., the thread has data
   * that has yet to be reported for more than MAX_BINS number of minutes), delete the oldest bin.
   * Defaulted to 10 because we can expect the histogram to be reported at least once every
   * 10 minutes.
   */
  private final static int MAX_BINS = 10;

  private final Supplier<Long> clockMillis;

  /**
   * Global concurrent list of ThreadMinuteBin.
   * This is ConcurrentLinkedDeque so that we can support 'flatMap(List::stream)' without
   * worrying about ConcurrentModificationException.
   */
  private final ConcurrentLinkedDeque<ThreadMinuteBin> globalHistogramBinsList = new ConcurrentLinkedDeque<>();

  /**
   * Current Minute Histogram Bin.
   * Update functions will only update data inside currentMinuteBin, which contains
   * Timestamp and the ConcurrentMap of ThreadId and TDigest distribution.
   */
  private ThreadMinuteBin currentMinuteBin;

  public WavefrontHistogramImpl() {
    this(System::currentTimeMillis);
  }

  public WavefrontHistogramImpl(Supplier<Long> clockMillis) {
    this.clockMillis = clockMillis;
    currentMinuteBin = new ThreadMinuteBin(this.currentMinuteMillis());
  }

  public void update(int value) {
    update((double) value);
  }

  public void update(long value) {
    update((double) value);
  }

  public void update(double value) {
    this.getCurrentBin().updateByThreadId(Thread.currentThread().getId(), value);
  }

  /**
   * Bulk-update this histogram with a set of centroids.
   *
   * @param means  the centroid values
   * @param counts the centroid weights/sample counts
   */
  public void bulkUpdate(List<Double> means, List<Integer> counts) {
    this.getCurrentBin().bulkUpdateByThreadId(Thread.currentThread().getId(), means, counts);
  }

  /**
   * @return returns the number of values in the distribution.
   */
  public long getCount() {
    return getGlobalHistogramBinsList().stream().filter(Objects::nonNull).
            flatMap(bin -> bin.perThreadDist.values().stream()).mapToLong(TDigest::size).sum();
  }

  /**
   * @return returns the maximum value in the distribution.
   * Returns NaN if the distribution is empty.
   */
  public double getMax() {
    return getGlobalHistogramBinsList().stream().filter(Objects::nonNull).
            flatMap(bin -> bin.perThreadDist.values().stream()).mapToDouble(TDigest::getMax).max().orElse(NaN);
  }

  /**
   * @return returns the minimum value in the distribution.
   * Returns NaN if the distribution is empty.
   */
  public double getMin() {
    return getGlobalHistogramBinsList().stream().filter(Objects::nonNull).
            flatMap(bin -> bin.perThreadDist.values().stream()).mapToDouble(TDigest::getMin).min().orElse(NaN);
  }

  /**
   * @return returns the mean of the values in the distribution.
   * Returns NaN if the distribution is empty.
   */
  public double getMean() {
    List<Centroid> centroids = new ArrayList<>();
    getGlobalHistogramBinsList().stream().filter(Objects::nonNull).
            forEach(bin -> centroids.addAll(bin.getCentroids()));
    return centroids.size() == 0 ? NaN : centroids.stream().
            mapToDouble(c -> (c.count() * c.mean()) / centroids.size()).sum();
  }

  /**
   * @return returns the sum of the values in the distribution.
   */
  public double getSum() {
    List<Centroid> centroids = new ArrayList<>();
    getGlobalHistogramBinsList().stream().filter(Objects::nonNull).
            forEach(bin -> centroids.addAll(bin.getCentroids()));
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
   * Aggregates all the minute bins prior to the current minute (because threads might be
   * updating the current minute bin while the method is invoked) and returns a list of the
   * distributions held within each bin. Note that invoking this method will also clear all data
   * from the aggregated bins, thereby changing the state of the system and preventing data from
   * being flushed more than once.
   *
   * @return returns a list of distributions, each a {@link Distribution} holding a timestamp
   * as well as a list of centroids. Each centroid is a tuple containing the centroid value and
   * count.
   */
  public List<Distribution> flushDistributions() {
    final List<Distribution> distributions = new ArrayList<>();
    Iterator<ThreadMinuteBin> binsIter = getGlobalHistogramBinsList().iterator();
    while (binsIter.hasNext()) {
      ThreadMinuteBin bin = binsIter.next();
      distributions.add(bin.toDistribution());
      binsIter.remove();
    }
    return distributions;
  }

  /**
   * @return returns a statistical {@link Snapshot} of the histogram distribution.
   */
  public Snapshot getSnapshot() {
    final TDigest snapshot = new AVLTreeDigest(ACCURACY);
    getGlobalHistogramBinsList().stream().filter(Objects::nonNull).
            flatMap(bin -> bin.perThreadDist.values().stream()).forEach(snapshot::add);
    return new Snapshot(snapshot);
  }

  private long currentMinuteMillis() {
    return (clockMillis.get() / 60000L) * 60000L;
  }

  /**
   * Helper to get the current bin.
   * Will flush currentMinuteBin into globalHistogramBinsList if it's a new minute.
   */
  private ThreadMinuteBin getCurrentBin() {
    long currMinuteMillis = currentMinuteMillis();
    if (this.currentMinuteBin.minuteMillis == currMinuteMillis) return this.currentMinuteBin;
    return flushCurrentBin(currMinuteMillis);
  }

  private ThreadMinuteBin flushCurrentBin(long currMinuteMillis) {
    synchronized(this) {
      if (this.currentMinuteBin.minuteMillis != currMinuteMillis) {
        if (globalHistogramBinsList.size() > MAX_BINS)
          this.globalHistogramBinsList.pollFirst();
        this.globalHistogramBinsList.offerLast(new ThreadMinuteBin(this.currentMinuteBin));
        this.currentMinuteBin = new ThreadMinuteBin(currMinuteMillis);
      }
      return this.currentMinuteBin;
    }
  }

  /**
   * Flush the current bin and return the globalHistogramBinsList.
   * The reason for flushing before return is because the currentMinuteBin might
   * not been updated for more than one minute. And since there's no update operation
   * after that. Thus currentMinuteBin of previous minute might not be added into
   * globalHistogramBinsList. Thus, flush it and return.
   */
  private ConcurrentLinkedDeque<ThreadMinuteBin> getGlobalHistogramBinsList() {
    flushCurrentBin(currentMinuteMillis());
    return globalHistogramBinsList;
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
     * @return returns the maximum value in the distribution.
     * Returns NaN if the distribution is empty.
     */
    public double getMax() {
      double max = distribution.getMax();
      return max == Double.NEGATIVE_INFINITY ? NaN : max;
    }

    /**
     * @return returns the minimum value in the distribution.
     * Returns NaN if the distribution is empty.
     */
    public double getMin() {
      double min = distribution.getMin();
      return min == Double.POSITIVE_INFINITY ? NaN : min;
    }

    /**
     * @return returns the mean of the values in the distribution.
     * Returns NaN if the distribution is empty.
     */
    public double getMean() {
      Collection<Centroid> centroids = distribution.centroids();
      return centroids.size() == 0 ? NaN : centroids.stream()
          .mapToDouble(c -> (c.count() * c.mean()) / centroids.size()).sum();
    }

    /**
     * @return returns the sum of the values in the distribution.
     */
    public double getSum() {
      return distribution.centroids().stream().mapToDouble(c -> c.count() * c.mean()).sum();
    }

    /**
     * @param quantile  a given quantile, between 0 and 1
     * @return returns the value in the distribution at the given quantile.
     * Returns NaN if the distribution is empty.
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
     * The list of histogram points, each a 2-dimensional {@link Pair} where the first dimension
     * is the mean value (Double) of the centroid and second dimension is the count of points in
     * that centroid.
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
  private static class ThreadMinuteBin {
    /**
     * Stores the {@link TDigest} Distribution for each threads in one given minute.
     */
    public ConcurrentMap<Long, TDigest> perThreadDist;

    /**
     * The timestamp at the start of the minute.
     */
    public final long minuteMillis;

    ThreadMinuteBin(long minuteMillis) {
      perThreadDist = new ConcurrentHashMap<>();
      this.minuteMillis = minuteMillis;
    }

    /**
     * Copy Constructor for appending ThreadMinuteBin to globalHistogramBinsList
     */
    ThreadMinuteBin(ThreadMinuteBin threadMinuteBin) {
      this.perThreadDist = new ConcurrentHashMap<>(threadMinuteBin.perThreadDist);
      this.minuteMillis = threadMinuteBin.minuteMillis;
    }

    /**
     * Helper to retrieve the thread-local distribution in one given minute.
     */
    TDigest getDistByThreadId(long threadId) {
      // Create new Digest for new thread.
      return perThreadDist.getOrDefault(threadId, new AVLTreeDigest(ACCURACY));
    }

    void updateByThreadId(long threadId, double value) {
      TDigest dist = this.getDistByThreadId(threadId);
      dist.add(value);
      this.perThreadDist.put(threadId, dist);
    }

    void bulkUpdateByThreadId(long threadId, List<Double> means, List<Integer> counts) {
      if (means != null && counts != null) {
        TDigest dist = this.getDistByThreadId(threadId);
        for (int i = 0; i < Math.min(means.size(), counts.size()); ++i)
          dist.add(means.get(i), counts.get(i));
        this.perThreadDist.put(threadId, dist);
      }
    }

    /**
     * Get list of centroids for distributions of all threads in this minute.
     */
    List<Centroid> getCentroids() {
        return perThreadDist.values().stream().flatMap(dist -> dist.centroids().stream())
                .collect(Collectors.toList());
    }

    /**
     * Convert to Distribution {@link Distribution}.
     */
    Distribution toDistribution() {
      return new Distribution(minuteMillis, perThreadDist.values().stream().
              flatMap(dist -> dist.centroids().stream()).
              map(c -> new Pair<>(c.mean(), c.count())).collect(Collectors.toList()));
    }
  }
}
