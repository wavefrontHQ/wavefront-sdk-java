package com.wavefront.sdk;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.direct_ingestion.WavefrontDirectIngestionClient;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.proxy.WavefrontProxyClient;

import java.io.IOException;
import java.util.UUID;

/**
 * Driver class for ad-hoc experiments
 *
 * @author Mori Bellamy (mori@wavefront.com).
 */
public class Main {

  private static void sendMetricViaProxy(WavefrontProxyClient wavefrontProxyClient)
      throws IOException {
    /*
     * Wavefront Metrics Data format
     * <metricName> <metricValue> [<timestamp>] source=<source> [pointTags]
     *
     * Example: "new-york.power.usage 42422 1533529977 source=localhost datacenter=dc1"
     */

    wavefrontProxyClient.sendMetric("new-york.power.usage", 42422.0, null,
        "localhost", ImmutableMap.<String, String>builder().build());
    System.out.println("Sent metric: 'new-york.power.usage' to proxy");
  }

  private static void sendMetricViaDirectIngestion(
      WavefrontDirectIngestionClient wavefrontDirectIngestionClient) throws IOException {
    /*
     * Wavefront Metrics Data format
     * <metricName> <metricValue> [<timestamp>] source=<source> [pointTags]
     *
     * Example: "new-york.power.usage 42422 1533529977 source=localhost datacenter=dc1"
     */

    wavefrontDirectIngestionClient.sendMetric("new-york.power.usage", 42422.0, null,
        "localhost", ImmutableMap.<String, String>builder().build());
    System.out.println("Sent metric: 'new-york.power.usage' to direct ingestion API");
  }

  private static void sendHistogramViaProxy(WavefrontProxyClient wavefrontProxyClient)
      throws IOException {
    /*
     * Wavefront Histogram Data format
     * {!M | !H | !D} [<timestamp>] #<count> <mean> [centroids] <histogramName> source=<source>
     *   [pointTags]
     *
     * Example: "!M 1533529977 #20 30.0 #10 5.1 request.latency source=appServer1 region=us-west"
     */
    wavefrontProxyClient.sendDistribution("request.latency",
        ImmutableList.<Pair<Double, Integer>>builder().
            add(new Pair<>(30.0, 20)).add(new Pair<>(5.1, 10)).build(),
        ImmutableSet.<HistogramGranularity>builder().add(HistogramGranularity.MINUTE).
            add(HistogramGranularity.HOUR).add(HistogramGranularity.DAY).build(),
        null, "appServer1",
        ImmutableMap.<String, String>builder().put("region", "us-west").build());
    System.out.println("Sent histogram: 'request.latency' to proxy");
  }

  private static void sendHistogramViaDirectIngestion(
      WavefrontDirectIngestionClient wavefrontDirectIngestionClient) throws IOException {
    /*
     * Wavefront Histogram Data format
     * {!M | !H | !D} [<timestamp>] #<count> <mean> [centroids] <histogramName> source=<source>
     *   [pointTags]
     *
     * Example: "!M 1533529977 #20 30.0 #10 5.1 request.latency source=appServer1 region=us-west"
     */
    wavefrontDirectIngestionClient.sendDistribution("request.latency",
        ImmutableList.<Pair<Double, Integer>>builder().
            add(new Pair<>(30.0, 20)).add(new Pair<>(5.1, 10)).build(),
        ImmutableSet.<HistogramGranularity>builder().add(HistogramGranularity.MINUTE).
            add(HistogramGranularity.HOUR).add(HistogramGranularity.DAY).build(),
        null, "appServer1",
        ImmutableMap.<String, String>builder().put("region", "us-west").build());
    System.out.println("Sent histogram: 'request.latency' to direction ingestion API");
  }

  private static void sendTracingSpanViaProxy(WavefrontProxyClient wavefrontProxyClient)
      throws IOException {
    /*
     * Wavefront Tracing Span Data format
     * <tracingSpanName> source=<source> [pointTags] <start_millis> <duration_micro_seconds>
     *
     * Example: "getAllUsers source=localhost
     *           traceId=7b3bf470-9456-11e8-9eb6-529269fb1459
     *           spanId=0313bafe-9457-11e8-9eb6-529269fb1459
     *           parent=2f64e538-9457-11e8-9eb6-529269fb1459
     *           application=Wavefront http.method=GET
     *           1533529977 343500"
     */

    wavefrontProxyClient.sendSpan("getAllUsers",1533529977L, 343500L, "localhost",
        UUID.fromString("7b3bf470-9456-11e8-9eb6-529269fb1459"),
        UUID.fromString("0313bafe-9457-11e8-9eb6-529269fb1459"),
        ImmutableList.<UUID>builder().add(UUID.fromString(
            "2f64e538-9457-11e8-9eb6-529269fb1459")).build(), null,
        ImmutableList.<Pair<String, String>>builder().
            add(new Pair<>("application", "Wavefront")).
            add(new Pair<>("http.method", "GET")).build(), null);
    System.out.println("Sent tracing span: 'getAllUsers' to proxy");
  }

  private static void sendTracingSpanViaDirectIngestion(
      WavefrontDirectIngestionClient wavefrontDirectIngestionClient) throws IOException {
    /*
     * Wavefront Tracing Span Data format
     * <tracingSpanName> source=<source> [pointTags] <start_millis> <duration_micro_seconds>
     *
     * Example: "getAllUsers source=localhost
     *           traceId=7b3bf470-9456-11e8-9eb6-529269fb1459
     *           spanId=0313bafe-9457-11e8-9eb6-529269fb1459
     *           parent=2f64e538-9457-11e8-9eb6-529269fb1459
     *           application=Wavefront http.method=GET
     *           1493773500 343500"
     */

    wavefrontDirectIngestionClient.sendSpan("getAllUsers",1493773500L, 343500L, "localhost",
        UUID.fromString("7b3bf470-9456-11e8-9eb6-529269fb1459"),
        UUID.fromString("0313bafe-9457-11e8-9eb6-529269fb1459"),
        ImmutableList.<UUID>builder().add(UUID.fromString(
            "2f64e538-9457-11e8-9eb6-529269fb1459")).build(), null,
        ImmutableList.<Pair<String, String>>builder().
            add(new Pair<>("application", "Wavefront")).
            add(new Pair<>("http.method", "GET")).build(), null);
    System.out.println("Sent tracing span: 'getAllUsers' to direct ingestion API");
  }

  public static void main(String[] args) throws InterruptedException, IOException {
    String wavefrontServer = args[0];
    String token = args[1];
    String proxyHost = args.length < 3 ? null : args[2];
    String metricsPort = args.length < 4 ? null : args[3];
    String distributionPort = args.length < 5 ? null : args[4];
    String tracingPort = args.length < 6 ? null : args[5];

    WavefrontProxyClient.Builder builder = new WavefrontProxyClient.Builder(proxyHost);
    if (metricsPort != null) {
      builder.metricsPort(Integer.parseInt(metricsPort));
    }
    if (distributionPort != null) {
      builder.distributionPort(Integer.parseInt(distributionPort));
    }
    if (tracingPort != null) {
      builder.tracingPort(Integer.parseInt(tracingPort));
    }
    WavefrontProxyClient wavefrontProxyClient = builder.build();

    WavefrontDirectIngestionClient wavefrontDirectIngestionClient =
        new WavefrontDirectIngestionClient.Builder(wavefrontServer, token).build();

    while (true) {
      // Send entities via Proxy
      sendMetricViaProxy(wavefrontProxyClient);
      sendHistogramViaProxy(wavefrontProxyClient);
      sendTracingSpanViaProxy(wavefrontProxyClient);
      wavefrontProxyClient.flush();

      // Send entities via Direct Ingestion
      sendMetricViaDirectIngestion(wavefrontDirectIngestionClient);
      sendHistogramViaDirectIngestion(wavefrontDirectIngestionClient);
      sendTracingSpanViaDirectIngestion(wavefrontDirectIngestionClient);

      Thread.sleep(5000);
    }
  }
}
