package com.wavefront.sdk;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.proxy.WavefrontProxyClient;

import org.junit.Test;

import java.util.UUID;

import static com.wavefront.sdk.common.Utils.histogramToLineData;
import static com.wavefront.sdk.common.Utils.metricToLineData;
import static com.wavefront.sdk.common.Utils.sanitize;
import static com.wavefront.sdk.common.Utils.tracingSpanToLineData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests for {@link WavefrontProxyClient}
 *
 * @author Clement Pang (clement@wavefront.com).
 */
public class WavefrontTest {

  @Test
  public void testSanitize() {
    assertEquals("\"hello\"", sanitize("hello"));
    assertEquals("\"hello-world\"", sanitize("hello world"));
    assertEquals("\"hello.world\"", sanitize("hello.world"));
    assertEquals("\"hello\\\"world\\\"\"", sanitize("hello\"world\""));
    assertEquals("\"hello'world\"", sanitize("hello'world"));
  }

  @Test
  public void testMetricToLineData() {
    assertEquals("\"new-york.power.usage\" 42422.0 1493773500 source=\"localhost\" " +
        "\"datacenter\"=\"dc1\"\n", metricToLineData("new-york.power.usage", 42422, 1493773500L,
        "localhost", ImmutableMap.<String, String>builder().put("datacenter", "dc1").build(),
        "defaultSource"));
    // null timestamp
    assertEquals("\"new-york.power.usage\" 42422.0 source=\"localhost\" " +
        "\"datacenter\"=\"dc1\"\n", metricToLineData("new-york.power.usage", 42422, null,
        "localhost", ImmutableMap.<String, String>builder().put("datacenter", "dc1").build(),
        "defaultSource"));
    // null tags
    assertEquals("\"new-york.power.usage\" 42422.0 1493773500 source=\"localhost\"\n",
        metricToLineData("new-york.power.usage", 42422, 1493773500L,
        "localhost", null, "defaultSource"));
    // null tags and null timestamp
    assertEquals("\"new-york.power.usage\" 42422.0 source=\"localhost\"\n",
        metricToLineData("new-york.power.usage", 42422, null, "localhost", null,
            "defaultSource"));
  }

  @Test
  public void testHistogramToLineData() {
    assertEquals("!M 1493773500 #20 30.0 #10 5.1 \"request.latency\" source=\"appServer1\" " +
            "\"region\"=\"us-west\"\n",
        histogramToLineData("request.latency", ImmutableList.<Pair<Double,
            Integer>>builder().add(new Pair<>(30.0, 20)).add(new Pair<>(5.1, 10)).build(),
        ImmutableSet.<HistogramGranularity>builder().add(HistogramGranularity.MINUTE).build(),
        1493773500L, "appServer1",
        ImmutableMap.<String, String>builder().put("region", "us-west").build(),
        "defaultSource"));

    // null timestamp
    assertEquals("!M #20 30.0 #10 5.1 \"request.latency\" source=\"appServer1\" " +
            "\"region\"=\"us-west\"\n",
        histogramToLineData("request.latency", ImmutableList.<Pair<Double,
                Integer>>builder().add(new Pair<>(30.0, 20)).add(new Pair<>(5.1, 10)).build(),
            ImmutableSet.<HistogramGranularity>builder().add(HistogramGranularity.MINUTE).build(),
            null, "appServer1",
            ImmutableMap.<String, String>builder().put("region", "us-west").build(),
            "defaultSource"));

    // null tags
    assertEquals("!M 1493773500 #20 30.0 #10 5.1 \"request.latency\" source=\"appServer1\"\n",
        histogramToLineData("request.latency", ImmutableList.<Pair<Double,
                Integer>>builder().add(new Pair<>(30.0, 20)).add(new Pair<>(5.1, 10)).build(),
            ImmutableSet.<HistogramGranularity>builder().add(HistogramGranularity.MINUTE).build(),
            1493773500L, "appServer1", null, "defaultSource"));

    // empty centroids
    try {
      assertEquals("!M 1493773500 #20 30.0 #10 5.1 \"request.latency\" source=\"appServer1\"\n",
          histogramToLineData("request.latency", ImmutableList.<Pair<Double,
                  Integer>>builder().build(),
              ImmutableSet.<HistogramGranularity>builder().add(HistogramGranularity.MINUTE).build(),
              1493773500L, "appServer1", null, "defaultSource"));
      fail();
    } catch(IllegalArgumentException ignored) {}

    // no histogram granularity specified
    try {
      assertEquals("!M 1493773500 #20 30.0 #10 5.1 \"request.latency\" source=\"appServer1\"\n",
          histogramToLineData("request.latency", ImmutableList.<Pair<Double,
                  Integer>>builder().add(new Pair<>(30.0, 20)).add(new Pair<>(5.1, 10)).build(),
              ImmutableSet.<HistogramGranularity>builder().build(),
              1493773500L, "appServer1", null, "defaultSource"));
      fail();
    } catch(IllegalArgumentException ignored) {}

    // multiple granularities
    assertEquals("!M 1493773500 #20 30.0 #10 5.1 \"request.latency\" source=\"appServer1\" \"region\"=\"us-west\"\n" +
            "!H 1493773500 #20 30.0 #10 5.1 \"request.latency\" source=\"appServer1\" \"region\"=\"us-west\"\n" +
            "!D 1493773500 #20 30.0 #10 5.1 \"request.latency\" source=\"appServer1\" \"region\"=\"us-west\"\n",
        histogramToLineData("request.latency", ImmutableList.<Pair<Double,
                Integer>>builder().add(new Pair<>(30.0, 20)).add(new Pair<>(5.1, 10)).build(),
            ImmutableSet.<HistogramGranularity>builder().add(HistogramGranularity.MINUTE).
                add(HistogramGranularity.HOUR).add(HistogramGranularity.DAY).build(),
            1493773500L, "appServer1",
            ImmutableMap.<String, String>builder().put("region", "us-west").build(),
            "defaultSource"));
  }

  @Test
  public void testTracingSpanToLineData() {
    assertEquals("\"getAllUsers\" source=\"localhost\" " +
            "traceId=7b3bf470-9456-11e8-9eb6-529269fb1459 spanId=0313bafe-9457-11e8-9eb6-529269fb1459 " +
            "parent=2f64e538-9457-11e8-9eb6-529269fb1459 " +
            "followsFrom=5f64e538-9457-11e8-9eb6-529269fb1459 " +
            "\"application\"=\"Wavefront\" " +
            "\"http.method\"=\"GET\" 1493773500 343500\n",
        tracingSpanToLineData("getAllUsers", 1493773500L, 343500L, "localhost",
            UUID.fromString("7b3bf470-9456-11e8-9eb6-529269fb1459"), UUID.fromString(
                "0313bafe-9457-11e8-9eb6-529269fb1459"), ImmutableList.<UUID>builder().add(
                UUID.fromString("2f64e538-9457-11e8-9eb6-529269fb1459")).build(),
            ImmutableList.<UUID>builder().add(
                UUID.fromString("5f64e538-9457-11e8-9eb6-529269fb1459")).build(),
            ImmutableList.<Pair<String, String>>builder().add(new Pair<>(
                "application", "Wavefront")).add(new Pair<>("http.method", "GET")).build(),
            null, "defaultSource"));

    // null followsFrom
    assertEquals("\"getAllUsers\" source=\"localhost\" " +
        "traceId=7b3bf470-9456-11e8-9eb6-529269fb1459 spanId=0313bafe-9457-11e8-9eb6-529269fb1459 " +
        "parent=2f64e538-9457-11e8-9eb6-529269fb1459 \"application\"=\"Wavefront\" " +
        "\"http.method\"=\"GET\" 1493773500 343500\n",
        tracingSpanToLineData("getAllUsers", 1493773500L, 343500L, "localhost",
        UUID.fromString("7b3bf470-9456-11e8-9eb6-529269fb1459"), UUID.fromString(
            "0313bafe-9457-11e8-9eb6-529269fb1459"), ImmutableList.<UUID>builder().add(
                UUID.fromString("2f64e538-9457-11e8-9eb6-529269fb1459")).build(), null,
        ImmutableList.<Pair<String, String>>builder().add(new Pair<>(
            "application", "Wavefront")).add(new Pair<>("http.method", "GET")).build(),
        null, "defaultSource"));

    // root span
    assertEquals("\"getAllUsers\" source=\"localhost\" " +
            "traceId=7b3bf470-9456-11e8-9eb6-529269fb1459 spanId=0313bafe-9457-11e8-9eb6-529269fb1459 " +
            "\"application\"=\"Wavefront\" " +
            "\"http.method\"=\"GET\" 1493773500 343500\n",
        tracingSpanToLineData("getAllUsers", 1493773500L, 343500L, "localhost",
            UUID.fromString("7b3bf470-9456-11e8-9eb6-529269fb1459"), UUID.fromString(
                "0313bafe-9457-11e8-9eb6-529269fb1459"), null, null,
            ImmutableList.<Pair<String, String>>builder().add(new Pair<>(
                "application", "Wavefront")).add(new Pair<>("http.method", "GET")).build(),
            null, "defaultSource"));

    // null tags
    assertEquals("\"getAllUsers\" source=\"localhost\" " +
            "traceId=7b3bf470-9456-11e8-9eb6-529269fb1459 spanId=0313bafe-9457-11e8-9eb6-529269fb1459 " +
            "1493773500 343500\n",
        tracingSpanToLineData("getAllUsers", 1493773500L, 343500L, "localhost",
            UUID.fromString("7b3bf470-9456-11e8-9eb6-529269fb1459"), UUID.fromString(
                "0313bafe-9457-11e8-9eb6-529269fb1459"), null, null, null, null,
            "defaultSource"));
  }
}
