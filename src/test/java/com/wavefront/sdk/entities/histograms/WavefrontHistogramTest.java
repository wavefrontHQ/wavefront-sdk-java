package com.wavefront.sdk.entities.histograms;

import com.google.common.primitives.Doubles;
import com.tdunning.math.stats.Centroid;
import org.hamcrest.collection.IsMapContaining;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static java.lang.Double.NaN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Basic unit tests around {@link WavefrontHistogram}
 *
 * @author Han Zhang (zhanghan@vmware.com)
 */
public class WavefrontHistogramTest {

  private static final double DELTA = 1e-1;

  private static AtomicLong clock;

  private static WavefrontHistogram pow10, inc100, inc1000;

  private static WavefrontHistogram createPow10Histogram(Supplier<Long> clockMillis) {
    WavefrontHistogram wh = new WavefrontHistogram(clockMillis);
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

  private Map<Double, Integer> distributionToMap(List<MinuteBin> bins) {
    Map<Double, Integer> map = new HashMap<>();

    for (MinuteBin minuteBin : bins) {
      StringBuilder sb = new StringBuilder();
      for (Centroid c : minuteBin.getDist().centroids()) {
        map.put(c.mean(), map.getOrDefault(c.mean(), 0) + c.count());
      }
    }

    return map;
  }

  @BeforeClass
  public static void setUp() {
    clock = new AtomicLong(System.currentTimeMillis());

    // WavefrontHistogram with values that are powers of 10
    pow10 = createPow10Histogram(clock::get);

    // WavefrontHistogram with a value for each integer from 1 to 100
    inc100 = new WavefrontHistogram(clock::get);
    for (int i = 1; i <= 100; i++) {
      inc100.update(i);
    }

    // WavefrontHistogram with a value for each integer from 1 to 1000
    inc1000 = new WavefrontHistogram(clock::get);
    for (int i = 1; i <= 1000; i++) {
      inc1000.update(i);
    }

    // Simulate that 1 min has passed so that values prior to the current min are ready to be read
    clock.addAndGet(60000L + 1);
  }

  @Test
  public void testDistribution() {
    WavefrontHistogram wh = createPow10Histogram(clock::get);
    clock.addAndGet(60000L + 1);

    List<MinuteBin> bins = wh.bins(true);
    Map<Double, Integer> map = distributionToMap(bins);

    assertEquals(7, map.size());
    assertThat(map, IsMapContaining.hasEntry(0.1, 1));
    assertThat(map, IsMapContaining.hasEntry(1.0, 1));
    assertThat(map, IsMapContaining.hasEntry(10.0, 2));
    assertThat(map, IsMapContaining.hasEntry(100.0, 1));
    assertThat(map, IsMapContaining.hasEntry(1000.0, 1));
    assertThat(map, IsMapContaining.hasEntry(10000.0, 2));
    assertThat(map, IsMapContaining.hasEntry(100000.0, 1));

    // check that the histogram has been cleared
    assertEquals(0, wh.getCount());
  }

  @Test
  public void testClear() {
    WavefrontHistogram wh = createPow10Histogram(clock::get);
    clock.addAndGet(60000L + 1);

    wh.clear();  // clears bins

    assertEquals(0, wh.getCount());
    assertEquals(NaN, wh.getMin(), DELTA);
    assertEquals(NaN, wh.getMax(), DELTA);
    assertEquals(NaN, wh.getMean(), DELTA);
    assertEquals(NaN, wh.getValue(.5), DELTA);
  }

  @Test
  public void testBulkUpdate() {
    WavefrontHistogram wh = new WavefrontHistogram(clock::get);
    wh.bulkUpdate(Doubles.asList(24.2, 84.35, 1002), Arrays.asList(80, 1, 9));
    clock.addAndGet(60000L + 1);

    List<MinuteBin> bins = wh.bins(true);
    Map<Double, Integer> map = distributionToMap(bins);

    assertEquals(3, map.size());
    assertThat(map, IsMapContaining.hasEntry(24.2, 80));
    assertThat(map, IsMapContaining.hasEntry(84.35, 1));
    assertThat(map, IsMapContaining.hasEntry(1002.0, 9));
  }

  @Test
  public void testCount() {
    assertEquals(9, pow10.getCount());
  }

  @Test
  public void testMin() {
    assertEquals(1, inc100.getMin(), DELTA);
  }

  @Test
  public void testMax() {
    assertEquals(100000, pow10.getMax(), DELTA);
  }

  @Test
  public void testMean() {
    assertEquals(13457.9, pow10.getMean(), DELTA);
  }

  @Test
  public void testQuantile() {
    assertEquals(100, pow10.getValue(.5), DELTA);
    assertEquals(25.5, inc100.getValue(.25), DELTA);
    assertEquals(75.5, inc100.getValue(.75), DELTA);
    assertEquals(95.5, inc100.getValue(.95), DELTA);
    assertEquals(98.5, inc100.getValue(.98), DELTA);
    assertEquals(99.5, inc100.getValue(.99), DELTA);
    assertEquals(999.5, inc1000.getValue(.999), DELTA);
  }
}
