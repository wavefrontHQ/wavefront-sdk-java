package com.wavefront.sdk.entities.histograms;

import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl.Distribution;
import com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl.Snapshot;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.util.*;
import java.util.concurrent.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static java.lang.Double.NaN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Basic unit tests around {@link WavefrontHistogramImpl}
 *
 * @author Han Zhang (zhanghan@vmware.com)
 */
public class WavefrontHistogramImplTest {

  private static final double DELTA = 1e-1;

  private static AtomicLong clock;

  private static WavefrontHistogramImpl pow10, inc100, inc1000, empty;

  private static WavefrontHistogramImpl createPow10Histogram(Supplier<Long> clockMillis) {
    WavefrontHistogramImpl wh = new WavefrontHistogramImpl(clockMillis);
    wh.update(0.1);
    wh.update(1);
    wh.update(10);
    wh.update(10);
    wh.update(100);
    wh.update(1000);
    wh.update(10000);
    wh.update(10000);
    wh.update(100000);

    return wh;
  }

  private Map<Double, Integer> distributionToMap(List<Distribution> distributions) {
    Map<Double, Integer> map = new HashMap<>();

    for (Distribution distribution : distributions) {
      for (Pair<Double, Integer> centroid : distribution.centroids) {
        map.put(centroid._1, map.getOrDefault(centroid._1, 0) + centroid._2);
      }
    }

    return map;
  }

  @BeforeAll
  public static void setUp() {
    clock = new AtomicLong(System.currentTimeMillis());

    // WavefrontHistogramImpl with values that are powers of 10
    pow10 = createPow10Histogram(clock::get);

    // WavefrontHistogramImpl with a value for each integer from 1 to 100
    inc100 = new WavefrontHistogramImpl(clock::get);
    for (int i = 1; i <= 100; i++) {
      inc100.update(i);
    }

    // WavefrontHistogramImpl with a value for each integer from 1 to 1000
    inc1000 = new WavefrontHistogramImpl(clock::get);
    for (int i = 1; i <= 1000; i++) {
      inc1000.update(i);
    }

    // Empty Wavefront Histogram
    empty = new WavefrontHistogramImpl(clock::get);

    // Simulate that 1 min has passed so that values prior to the current min are ready to be read
    clock.addAndGet(60000L + 1);
  }

  @Test
  public void testDistribution() {
    WavefrontHistogramImpl wh = createPow10Histogram(clock::get);
    clock.addAndGet(60000L + 1);

    List<Distribution> distributions = wh.flushDistributions();
    Map<Double, Integer> map = distributionToMap(distributions);

    assertEquals(7, map.size());
    assertTrue(map.containsKey(0.1) && map.get(0.1) == 1);
    assertTrue(map.containsKey(1.0) && map.get(1.0) == 1);
    assertTrue(map.containsKey(10.0) && map.get(10.0) == 2);
    assertTrue(map.containsKey(100.0) && map.get(100.0) == 1);
    assertTrue(map.containsKey(1000.0) && map.get(1000.0) == 1);
    assertTrue(map.containsKey(10000.0) && map.get(10000.0) == 2);
    assertTrue(map.containsKey(100000.0) && map.get(100000.0) == 1);

    // check that the histogram has been cleared
    assertEquals(0, wh.getCount());
    assertEquals(NaN, wh.getMax(), DELTA);
    assertEquals(NaN, wh.getMin(), DELTA);
    assertEquals(NaN, wh.getMean(), DELTA);
    assertEquals(0, wh.getSum(), DELTA);

    Snapshot snapshot = wh.getSnapshot();
    assertEquals(0, snapshot.getCount());
    assertEquals(NaN, snapshot.getMax(), DELTA);
    assertEquals(NaN, snapshot.getMin(), DELTA);
    assertEquals(NaN, snapshot.getMean(), DELTA);
    assertEquals(NaN, snapshot.getValue(0.5), DELTA);
    assertEquals(0, snapshot.getSum(), DELTA);
  }

  @Test
  public void testBulkUpdate() {
    WavefrontHistogramImpl wh = new WavefrontHistogramImpl(clock::get);
    wh.bulkUpdate(Arrays.asList(24.2, 84.35, 1002.0), Arrays.asList(80, 1, 9));
    clock.addAndGet(60000L + 1);

    List<Distribution> distributions = wh.flushDistributions();
    Map<Double, Integer> map = distributionToMap(distributions);

    assertEquals(3, map.size());
    assertTrue(map.containsKey(24.2) && map.get(24.2) == 80);
    assertTrue(map.containsKey(84.35) && map.get(84.35) == 1);
    assertTrue(map.containsKey(1002.0) && map.get(1002.0) == 9);
  }

  @Test
  public void testCount() {
    assertEquals(9, pow10.getCount());
    assertEquals(9, pow10.getSnapshot().getCount());
  }

  @Test
  public void testMax() {
    assertEquals(100000, pow10.getMax(), DELTA);
    assertEquals(100000, pow10.getSnapshot().getMax(), DELTA);
  }

  @Test
  public void testMin() {
    assertEquals(1, inc100.getMin(), DELTA);
    assertEquals(1, inc100.getSnapshot().getMin(), DELTA);
  }

  @Test
  public void testMean() {
    assertEquals(13457.9, pow10.getMean(), DELTA);
    assertEquals(13457.9, pow10.getSnapshot().getMean(), DELTA);
  }

  @Test
  public void testSum() {
    assertEquals(121121.1, pow10.getSum(), DELTA);
    assertEquals(121121.1, pow10.getSnapshot().getSum(), DELTA);
  }

  @Test
  public void testSize() {
    assertEquals(9, pow10.getSnapshot().getSize());
    assertEquals(100, inc100.getSnapshot().getSize());
    assertEquals(1000, inc1000.getSnapshot().getSize());
  }

  @Test
  public void testStdDev() {
    assertEquals(30859.85264838444, pow10.stdDev(), DELTA);
    assertEquals(28.86607004772212, inc100.stdDev(), DELTA);
    assertEquals(288.6749902572095, inc1000.stdDev(), DELTA);
    assertEquals(0, empty.stdDev());
  }

  @Test
  public void testQuantile() {
    assertEquals(100, pow10.getSnapshot().getValue(.5), DELTA);

    Snapshot snapshot = inc100.getSnapshot();
    assertEquals(25.5, snapshot.getValue(.25), DELTA);
    assertEquals(75.5, snapshot.getValue(.75), DELTA);
    assertEquals(95.5, snapshot.getValue(.95), DELTA);
    assertEquals(98.5, snapshot.getValue(.98), DELTA);
    assertEquals(99.5, snapshot.getValue(.99), DELTA);

    assertEquals(999.5, inc1000.getSnapshot().getValue(.999), DELTA);
  }

  @Test
  public void testWavefrontHistogramThreaded() {
    AtomicLong clock = new AtomicLong(System.currentTimeMillis());
    WavefrontHistogramImpl wh = new WavefrontHistogramImpl(clock::get);

    ThreadPoolExecutor e = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
    e.execute(() -> {
      for (int i = 0; i < 500; i++) {
        int[] samples = {100, 66, 37, 8, 7, 5, 1};
        for (int sample : samples) {
          if (i % sample == 0) {
            wh.update(sample);
            break;
          }
        }
      }
    });
    for (int i = 0; i < 500; i++) {
      int[] samples = {100, 66, 37, 8, 7, 5, 1};
      for (int sample : samples) {
        if (i % sample == 0) {
          wh.update(sample);
          break;
        }
      }
    }
    while (e.getActiveCount() > 0) {}

    // Advance the clock by 1 min ...
    clock.addAndGet(60000L + 1);

    List<Distribution> distributions = wh.flushDistributions();
    Map<Double, Integer> map = distributionToMap(distributions);

    assertEquals(7, map.size());
    assertTrue(map.containsKey(1.0) && map.get(1.0) == 574);
    assertTrue(map.containsKey(5.0) && map.get(5.0) == 138);
    assertTrue(map.containsKey(7.0) && map.get(7.0) == 122);
    assertTrue(map.containsKey(8.0) && map.get(8.0) == 116);
    assertTrue(map.containsKey(37.0) && map.get(37.0) == 26);
    assertTrue(map.containsKey(66.0) && map.get(66.0) == 14);
    assertTrue(map.containsKey(100.0) && map.get(100.0) == 10);
  }

  @Disabled("Single Thread Update Benchmark")
  @Test
  public void singleThreadUpdateBenchmark() {
    int duration = 1; // Benchmark for 1 Minute
    WavefrontHistogramImpl wh = new WavefrontHistogramImpl(System::currentTimeMillis);
    Pair<Long, Long> result = this.singleThreadUpdateBenchmark(duration, wh, true);
    System.out.println("single thread: update() " + result._1 + " times in " +
            duration + " minutes");
  }

  private Pair<Long, Long> singleThreadUpdateBenchmark(int duration, WavefrontHistogramImpl wh, boolean enableFlush) {
    long updateCount = 0L;
    long flushCount = 0L;
    for (int min = 0; min < duration; min++) {
      long start = System.currentTimeMillis();
      while (System.currentTimeMillis() - start < 60001) {
        wh.update(Math.random() * 1000);
        updateCount++;
      }
      if (enableFlush) {
        wh.flushDistributions();
        flushCount++;
      }
    }
    return new Pair<>(updateCount, flushCount);
  }

  @Disabled("Single Thread Update & Flush Benchmark")
  @Test
  public void singleThreadFlushBenchmark() {
    int duration = 1; // Benchmark for 1 Minute
    WavefrontHistogramImpl wh = new WavefrontHistogramImpl(clock::get);
    Pair<Long, Long> result = this.singleThreadFlushBenchmark(duration, wh, true);
    System.out.println("single thread: update() " + result._1 + " times and " +
            "flush() " + result._2 + " times in " + duration + " minutes");
  }

  private Pair<Long, Long> singleThreadFlushBenchmark(int duration, WavefrontHistogramImpl wh, boolean enableFlush) {
    long updateCount = 0L;
    long flushCount = 0L;
    for (int min = 0; min < duration; min++) {
      long start = System.currentTimeMillis();
      while (System.currentTimeMillis() - start < 60000) {
        for (int sec = 0; sec < 60; sec++) {
          wh.update(Math.random() * 1000);
          if (enableFlush) clock.addAndGet(1000L);
          updateCount++;
        }
        if (enableFlush) {
          clock.addAndGet(1L);
          wh.flushDistributions();
          flushCount++;
        }
      }
    }
    return new Pair<>(updateCount, flushCount);
  }

  @Disabled("Multi-Thread Update Benchmark")
  @Test
  public void multiThreadUpdateBenchmark() {
    int threadNum = 4;
    int duration = 1; // Benchmark for 1 Minute
    WavefrontHistogramImpl wh = new WavefrontHistogramImpl(System::currentTimeMillis);
    Supplier<Pair<Long, Long>> flushAndUpdateBenchmark =
            () -> this.singleThreadUpdateBenchmark(duration, wh, true);
    Supplier<Pair<Long, Long>> updateBenchmark =
            () -> this.singleThreadUpdateBenchmark(duration, wh, false);
    Pair<Long, Long> result = multiThreadBenchmark(threadNum, flushAndUpdateBenchmark, updateBenchmark);
    System.out.println(threadNum + " threads: update() " + result._1 + " times in " +
            duration + " minutes");
  }

  @Disabled("Multi-Thread Update & Flush Benchmark")
  @Test
  public void multiThreadFlushBenchmark() {
    int threadNum = 4;
    int duration = 1; // Benchmark for 1 Minute
    WavefrontHistogramImpl wh = new WavefrontHistogramImpl(clock::get);
    Supplier<Pair<Long, Long>> flushAndUpdateBenchmark =
            () -> this.singleThreadFlushBenchmark(duration, wh, true);
    Supplier<Pair<Long, Long>> updateBenchmark =
            () -> this.singleThreadFlushBenchmark(duration, wh, false);
    Pair<Long, Long> result = multiThreadBenchmark(threadNum, flushAndUpdateBenchmark, updateBenchmark);
    System.out.println(threadNum + " threads: update() " + result._1 + " times and " +
            "flush() " + result._2 + " times in " + duration + " minutes");
  }

  private Pair<Long, Long> multiThreadBenchmark(int threadNum, Supplier<Pair<Long, Long>> flushAndUpdateBenchmark,
                                                Supplier<Pair<Long, Long>> updateBenchmark) {
    ExecutorService pool = Executors.newFixedThreadPool(threadNum);
    List<Future<Pair<Long, Long>>> results = new ArrayList<>(threadNum);
    Callable<Pair<Long, Long>> flushAndUpdateWorker = flushAndUpdateBenchmark::get;
    Callable<Pair<Long, Long>> updateWorker = updateBenchmark::get;
    results.add(pool.submit(flushAndUpdateWorker));
    for (int i = 0; i < threadNum - 1; i++) {
      results.add(pool.submit(updateWorker));
    }
    long updateCount = 0L;
    long flushCount = 0L;
    for (Future<Pair<Long, Long>> result : results) {
      try {
        updateCount += result.get()._1;
        flushCount += result.get()._2;
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
    }
    return new Pair<>(updateCount, flushCount);
  }
}
