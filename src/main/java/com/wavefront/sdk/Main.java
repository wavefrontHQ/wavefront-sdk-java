package com.wavefront.sdk;

import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.direct.ingestion.WavefrontDirectIngestionClient;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.proxy.WavefrontMultiProxyClient;
import com.wavefront.sdk.proxy.WavefrontProxyClient;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Driver class for ad-hoc experiments
 *
 * @author Mori Bellamy (mori@wavefront.com).
 */
public class Main {

  private static void sendMetric(WavefrontSender wavefrontSender)
      throws IOException {
    /*
     * Wavefront Metrics Data format
     * <metricName> <metricValue> [<timestamp>] source=<source> [pointTags]
     *
     * Example: "new-york.power.usage 42422 1533529977 source=localhost datacenter=dc1"
     */

    Map<String, String> tags = new HashMap<String, String>() {{
      put("datacenter", "dc1");
    }};
    wavefrontSender.sendMetric("new-york.power.usage", 42422.0, null,
        "localhost", tags);
    System.out.println("Sent metric: 'new-york.power.usage' to proxy");
  }

  private static void sendDeltaCounter(WavefrontSender wavefrontSender)
      throws IOException {
    /*
     * Wavefront Delta Counter format
     * <metricName> <metricValue> source=<source> [pointTags]
     *
     * Example: "lambda.thumbnail.generate 10 source=lambda_thumbnail_service image-format=jpeg"
     */

    Map<String, String> tags = new HashMap<String, String>() {{
      put("image-format", "jpeg");
    }};
    wavefrontSender.sendDeltaCounter("lambda.thumbnail.generate", 10,
        "lambda_thumbnail_service", tags);
    System.out.println("Sent metric: 'lambda.thumbnail.generate' to proxy");
  }

  private static void sendHistogram(WavefrontSender wavefrontSender)
      throws IOException {
    /*
     * Wavefront Histogram Data format
     * {!M | !H | !D} [<timestamp>] #<count> <mean> [centroids] <histogramName> source=<source>
     *   [pointTags]
     *
     * Example: "!M 1533529977 #20 30.0 #10 5.1 request.latency source=appServer1 region=us-west"
     */
    Map<String, String> tags = new HashMap<String, String>() {{
      put("region", "us-west");
    }};
    Set<HistogramGranularity> histogramGranularities = new HashSet<>();
    histogramGranularities.add(HistogramGranularity.MINUTE);
    histogramGranularities.add(HistogramGranularity.HOUR);
    histogramGranularities.add(HistogramGranularity.DAY);
    wavefrontSender.sendDistribution("request.latency",
        Arrays.asList(new Pair<>(30.0, 20), new Pair<>(5.1, 10)), histogramGranularities,
        null, "appServer1", tags);
    System.out.println("Sent histogram: 'request.latency' to proxy");
  }

  private static void sendTracingSpan(WavefrontSender wavefrontSender)
      throws IOException {
    /*
     * Wavefront Tracing Span Data format
     * <tracingSpanName> source=<source> [pointTags] <start_millis> <duration_milli_seconds>
     *
     * Example: "getAllUsers source=localhost
     *           traceId=7b3bf470-9456-11e8-9eb6-529269fb1459
     *           spanId=0313bafe-9457-11e8-9eb6-529269fb1459
     *           parent=2f64e538-9457-11e8-9eb6-529269fb1459
     *           application=Wavefront http.method=GET
     *           1533529977 343500"
     */

    wavefrontSender.sendSpan("getAllUsers",1533529977L, 343500L, "localhost",
        UUID.fromString("7b3bf470-9456-11e8-9eb6-529269fb1459"),
        UUID.fromString("0313bafe-9457-11e8-9eb6-529269fb1459"),
        Arrays.asList(UUID.fromString(
            "2f64e538-9457-11e8-9eb6-529269fb1459")), null,
        Arrays.asList(new Pair<>("application", "Wavefront"),
            new Pair<>("http.method", "GET")), null);
    System.out.println("Sent tracing span: 'getAllUsers' to proxy");
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

    WavefrontMultiProxyClient.Builder mcBuilder = new WavefrontMultiProxyClient.Builder();
    mcBuilder.withWavefrontProxyClient(proxyHost, wavefrontProxyClient);
    WavefrontMultiProxyClient wavefrontMultiProxyClient = mcBuilder.build();
    wavefrontMultiProxyClient.getClient<WavefrontProxyClient>("foo");

    WavefrontDirectIngestionClient wavefrontDirectIngestionClient =
        new WavefrontDirectIngestionClient.Builder(wavefrontServer, token).build();

    while (true) {
      // Send entities via Proxy
      sendMetric(wavefrontProxyClient);
      sendDeltaCounter(wavefrontProxyClient);
      sendHistogram(wavefrontProxyClient);
      sendTracingSpan(wavefrontProxyClient);
      wavefrontProxyClient.flush();

      // Send entities via Direct Ingestion
      sendMetric(wavefrontDirectIngestionClient);
      sendDeltaCounter(wavefrontDirectIngestionClient);
      sendHistogram(wavefrontDirectIngestionClient);
      sendTracingSpan(wavefrontDirectIngestionClient);

      // Send entities via the Multi Proxy Client
      sendMetric(wavefrontMultiProxyClient);
      sendDeltaCounter(wavefrontMultiProxyClient);
      sendHistogram(wavefrontMultiProxyClient);
      sendTracingSpan(wavefrontMultiProxyClient);
      wavefrontMultiProxyClient.flush();

      Thread.sleep(5000);
    }
  }
}
