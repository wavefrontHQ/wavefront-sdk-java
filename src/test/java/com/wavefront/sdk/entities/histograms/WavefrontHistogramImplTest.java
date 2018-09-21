package com.wavefront.sdk.entities.histograms;

import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl.Distribution;
import com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl.Snapshot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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

  private static WavefrontHistogramImpl pow10, inc100, inc1000;

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
    assertEquals(NaN, pow10.stdDev(), DELTA);
    assertEquals(NaN, inc100.stdDev(), DELTA);
    assertEquals(NaN, inc1000.stdDev(), DELTA);
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
}
