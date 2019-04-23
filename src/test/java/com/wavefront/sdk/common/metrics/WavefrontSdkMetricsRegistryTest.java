package com.wavefront.sdk.common.metrics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link WavefrontSdkMetricsRegistry}
 *
 * @author Han Zhang (zhanghan@vmware.com).
 */
public class WavefrontSdkMetricsRegistryTest {
  @Test
  public void testGauge() {
    WavefrontSdkMetricsRegistry registry = new WavefrontSdkMetricsRegistry.Builder(null).
        reportingIntervalSeconds(Integer.MAX_VALUE).
        build();
    List<Integer> list = new ArrayList<>();
    WavefrontSdkGauge gauge = registry.newGauge("gauge", list::size);
    assertEquals(0, gauge.getValue());
    list.add(0);
    assertEquals(1, gauge.getValue());
  }

  @Test
  public void testCounter() {
    WavefrontSdkMetricsRegistry registry = new WavefrontSdkMetricsRegistry.Builder(null).
        reportingIntervalSeconds(Integer.MAX_VALUE).
        build();
    WavefrontSdkCounter counter = registry.newCounter("counter");
    assertEquals(0, counter.count());
    counter.inc();
    assertEquals(1, counter.count());
    counter.inc(2);
    assertEquals(3, counter.count());
    counter.clear();
    assertEquals(0, counter.count());
    counter.inc(5);
    assertEquals(5, registry.newCounter("counter").count());
    assertEquals(0, registry.newCounter("counter2").count());
  }
}
