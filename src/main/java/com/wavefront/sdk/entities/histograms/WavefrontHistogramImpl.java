package com.wavefront.sdk.entities.histograms;

import com.tdunning.math.stats.AVLTreeDigest;
import com.tdunning.math.stats.Centroid;
import com.tdunning.math.stats.TDigest;
import com.wavefront.sdk.common.Pair;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.lang.Double.NaN;

/**
 * Wavefront implementation of a histogram
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 * @version $Id: $Id
 */
public class WavefrontHistogramImpl {
  /**
   * We support approx 32 centroids for every minute bin T-Digest distributions
   */
  private final static int ACCURACY = 32;

  /**
   * Re-compress the centroids when their number exceeds {@code #ACCURACY} multiplied
   * by this bloat factor.
   */
  private static final double RECOMPRESSION_THRESHOLD_FACTOR = 2.0;

  /**
   * If a thread's bin queue has exceeded MAX_BINS number of bins (e.g., the thread has data
   * that has yet to be reported for more than MAX_BINS number of minutes), delete the oldest bin.
   * Defaulted to 10 because we can expect the histogram to be reported at least once every
   * 10 minutes.
   */
  private final static int MAX_BINS = 10;

  private final Supplier<Long> clockMillis;

  /**
   * Global concurrent list of thread local histogramBinsList wrapped in WeakReference.
   * This list holds all the thread local List of Minute Bins.
   * This is ConcurrentLinkedDeque so that we can support 'flatMap(List::stream)' without
   * worrying about ConcurrentModificationException.
   * The MinuteBin itself is not thread safe and can change but it is still thread safe since we
   * don’t ever update a bin that’s old or flush a bin that’s within the current minute.
   */
  private final List<WeakReference<ConcurrentLinkedDeque<MinuteBin>>> globalHistogramBinsList =
      new ArrayList<>();

  private final StampedLock stampedLock = new StampedLock();
  // Protects read access to globalHistogramBinsList
  private final Lock readLock = stampedLock.asReadLock();

  // Protects write access to globalHistogramBinsList
  private final Lock writeLock = stampedLock.asWriteLock();

  /**
   * ThreadLocal histogramBinsList where the initial value set is also added to a
   * global list of thread local histogramBinsList wrapped in WeakReference
   */
  private final ThreadLocal<ConcurrentLinkedDeque<MinuteBin>> histogramBinsList =
      ThreadLocal.withInitial(() -> {
        ConcurrentLinkedDeque<MinuteBin> sharedBinsInstance = new ConcurrentLinkedDeque<>();
        try {
          writeLock.lock();
          globalHistogramBinsList.add(new WeakReference<>(sharedBinsInstance));
        } finally {
          writeLock.unlock();
        }
        return sharedBinsInstance;
  });

  /**
   * <p>Constructor for WavefrontHistogramImpl.</p>
   */
  public WavefrontHistogramImpl() {
    this(System::currentTimeMillis);
  }

  /**
   * <p>Constructor for WavefrontHistogramImpl.</p>
   *
   * @param clockMillis a {@link java.util.function.Supplier} object
   */
  public WavefrontHistogramImpl(Supplier<Long> clockMillis) {
    this.clockMillis = clockMillis;
  }

  /**
   * <p>update.</p>
   *
   * @param value a int
   */
  public void update(int value) {
    update((double) value);
  }

  /**
   * <p>update.</p>
   *
   * @param value a long
   */
  public void update(long value) {
    update((double) value);
  }

  /**
   * <p>update.</p>
   *
   * @param value a double
   */
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
   * <p>getCount.</p>
   *
   * @return returns the number of values in the distribution.
   */
  public long getCount() {
    try {
      readLock.lock();
      return globalHistogramBinsList.stream().map(Reference::get).filter(Objects::nonNull).
          flatMap(Collection::stream).mapToLong(bin -> bin.distribution.size()).sum();
    } finally {
      readLock.unlock();
    }
  }

  /**
   * <p>getMax.</p>
   *
   * @return returns the maximum value in the distribution.
   * Returns NaN if the distribution is empty.
   */
  public double getMax() {
    try {
      readLock.lock();
      return globalHistogramBinsList.stream().map(Reference::get).filter(Objects::nonNull).
          flatMap(Collection::stream).
          mapToDouble(bin -> bin.distribution.getMax()).max().orElse(NaN);
    } finally {
      readLock.unlock();
    }
  }

  /**
   * <p>getMin.</p>
   *
   * @return returns the minimum value in the distribution.
   * Returns NaN if the distribution is empty.
   */
  public double getMin() {
    try {
      readLock.lock();
      return globalHistogramBinsList.stream().map(Reference::get).filter(Objects::nonNull).
          flatMap(Collection::stream).
          mapToDouble(bin -> bin.distribution.getMin()).min().orElse(NaN);
    } finally {
      readLock.unlock();
    }
  }

  /**
   * <p>getMean.</p>
   *
   * @return returns the mean of the values in the distribution.
   * Returns NaN if the distribution is empty.
   */
  public double getMean() {
    final List<Centroid> centroids = getCentroids();
    Centroid mean = getMeanCentroid(centroids);
    return getMean(centroids, mean);
  }

  private Centroid getMeanCentroid(Collection<Centroid> centroids) {
    return centroids.stream().reduce((x, y) -> new Centroid(x.mean() + (y.mean() * y.count()),
        x.count() + y.count())).orElse(null);
  }

  private double getMean(Collection<Centroid> centroids, Centroid mean) {
    return mean == null || centroids.size() == 0 ? Double.NaN : mean.mean() / mean.count();
  }

  /**
   * <p>getSum.</p>
   *
   * @return returns the sum of the values in the distribution.
   */
  public double getSum() {
    return getCentroids().stream().mapToDouble(c -> c.count() * c.mean()).sum();
  }

  /**
   * <p>stdDev.</p>
   *
   * @return returns the stdDev of the values in the distribution
   */
  public double stdDev() {
    List<Centroid> centroids = getCentroids();

    double mean = getMean(centroids, getMeanCentroid(centroids));
    double varianceSum = centroids.stream().mapToDouble(c -> {
      double diff = c.mean() - mean;
      return diff * diff * c.count();
    }).sum();
    double count = centroids.stream().mapToDouble(Centroid::count).sum();
    double variance = count == 0 ? 0 : varianceSum / count;
    return Math.sqrt(variance);
  }

  /**
   * This method is protected against race condition via a Read Lock.
   *
   * @return list of aggregated centroids in memory
   */
  private List<Centroid> getCentroids() {
    List<Centroid> centroids = new ArrayList<>();
    try {
      readLock.lock();
      globalHistogramBinsList.stream().map(Reference::get).filter(Objects::nonNull).
          flatMap(Collection::stream).
          forEach(bin -> centroids.addAll(bin.distribution.centroids()));
    } finally {
      readLock.unlock();
    }
    return centroids;
  }

  /**
   * Aggregates all the minute bins prior to the current minute (because threads might be
   * updating the current minute bin while the method is invoked) and returns a list of the
   * distributions held within each bin. Note that invoking this method will also clear all data
   * from the aggregated bins, thereby changing the state of the system and preventing data from
   * being flushed more than once.
   *
   * @return returns a list of distributions, each a {@link com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl.Distribution} holding a timestamp
   * as well as a list of centroids. Each centroid is a tuple containing the centroid value and
   * count.
   */
  public List<Distribution> flushDistributions() {
    final long cutoffMillis = currentMinuteMillis();
    try {
      writeLock.lock();
      return processGlobalHistogramBinsList(cutoffMillis);
    } finally {
      writeLock.unlock();
    }
  }

  private List<Distribution> processGlobalHistogramBinsList(long cutoffMillis) {
    final List<Distribution> distributions = new ArrayList<>();
    Iterator<WeakReference<ConcurrentLinkedDeque<MinuteBin>>> globalBinsIter =
        globalHistogramBinsList.iterator();
    Map<Long, MinuteBin> mergedBins = new HashMap<>();
    while (globalBinsIter.hasNext()) {
      WeakReference<ConcurrentLinkedDeque<MinuteBin>> weakRef = globalBinsIter.next();
      ConcurrentLinkedDeque<MinuteBin> sharedBinsInstance = weakRef.get();
      if (sharedBinsInstance == null) {
        // Weak reference already garbage collected, hence remove the weakRef from global list
        globalBinsIter.remove();
        continue;
      }

      Iterator<MinuteBin> binsIter = sharedBinsInstance.iterator();
      while (binsIter.hasNext()) {
        MinuteBin minuteBin = binsIter.next();
        if (minuteBin.minuteMillis < cutoffMillis) {
          mergedBins.compute(minuteBin.minuteMillis, (k, v) -> {
            if (v == null) {
              return minuteBin;
            } else {
              v.distribution.add(minuteBin.distribution);
              return v;
            }
          });
          binsIter.remove();
        }
      }
    }
    mergedBins.forEach((key, value) -> {
      if (value.distribution.centroidCount() > ACCURACY * RECOMPRESSION_THRESHOLD_FACTOR) {
        value.distribution.compress();
      }
      List<Pair<Double, Integer>> centroids = value.distribution.centroids().stream().
          map(c -> new Pair<>(c.mean(), c.count())).collect(Collectors.toList());
      distributions.add(new Distribution(key, centroids));
    });
    return distributions;
  }

  /**
   * <p>getSnapshot.</p>
   *
   * @return returns a statistical {@link com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl.Snapshot} of the histogram distribution.
   */
  public Snapshot getSnapshot() {
    final TDigest snapshot = new AVLTreeDigest(ACCURACY);
    try {
      readLock.lock();
      globalHistogramBinsList.stream().map(Reference::get).filter(Objects::nonNull).
          flatMap(Collection::stream).forEach(bin -> snapshot.add(bin.distribution));
    } finally {
      readLock.unlock();
    }
    if (snapshot.centroidCount() > ACCURACY * RECOMPRESSION_THRESHOLD_FACTOR) {
      snapshot.compress();
    }
    return new Snapshot(snapshot);
  }

  private long currentMinuteMillis() {
    return (clockMillis.get() / 60000L) * 60000L;
  }

  /**
   * Helper to retrieve the current bin. Will be invoked on the thread local histogramBinsList.
   */
  private MinuteBin getCurrentBin() {
    ConcurrentLinkedDeque<MinuteBin> sharedBinsInstance = histogramBinsList.get();
    long currMinuteMillis = currentMinuteMillis();
    if (sharedBinsInstance.isEmpty() ||
        sharedBinsInstance.getLast().minuteMillis != currMinuteMillis) {
      sharedBinsInstance.add(new MinuteBin(ACCURACY, currMinuteMillis));
      if (sharedBinsInstance.size() > MAX_BINS) {
        sharedBinsInstance.removeFirst();
      }
    }
    return sharedBinsInstance.getLast();
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
      Centroid mean = centroids.stream().
              reduce((x, y) -> new Centroid(x.mean() + (y.mean() * y.count()), x.count() + y.count())).orElse(null);
      return mean == null || centroids.size() == 0 ? Double.NaN : mean.mean() / mean.count();
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
