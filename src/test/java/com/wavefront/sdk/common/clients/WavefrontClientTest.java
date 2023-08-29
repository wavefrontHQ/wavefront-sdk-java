package com.wavefront.sdk.common.clients;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.clients.service.token.CSPTokenService;
import com.wavefront.sdk.common.clients.service.token.NoopProxyTokenService;
import com.wavefront.sdk.common.clients.service.token.WavefrontTokenService;
import com.wavefront.sdk.common.metrics.WavefrontSdkDeltaCounter;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.tracing.SpanLog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.ServerSocket;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the {@link WavefrontClient} class
 *
 * @author Mike McMahon (mike.mcmahon@wavefront.com)
 */
public class WavefrontClientTest {
  @Test
  public void testGetBatch() {
    int batchSize = 8;
    int messageSizeBytes = 200;

    LinkedBlockingQueue<String> buffer = new LinkedBlockingQueue<>();
    buffer.offer(createString(50));   // chunk 1
    buffer.offer(createString(50));   // chunk 1
    buffer.offer(createString(50));   // chunk 1
    buffer.offer(createString(100));  // chunk 2
    buffer.offer(createString(250));  // dropped
    buffer.offer(createString(100));  // chunk 2
    buffer.offer(createString(200));  // chunk 3
    buffer.offer(createString(50));   // chunk 4
    buffer.offer(createString(50));   // chunk 4
    buffer.offer(createString(50));   // remains in buffer

    WavefrontSdkDeltaCounter dropped = createMock(WavefrontSdkDeltaCounter.class);
    dropped.inc();
    expectLastCall().once();

    replay(dropped);
    List<List<String>> batch = WavefrontClient.getBatch(buffer, batchSize,
        messageSizeBytes, dropped);
    verify(dropped);

    assertEquals(4, batch.size());
    assertEquals(3, batch.get(0).size());
    assertEquals(2, batch.get(1).size());
    assertEquals(1, batch.get(2).size());
    assertEquals(2, batch.get(3).size());
    assertEquals(1, buffer.size());
  }

  private String createString(int size) {
    return new String(new char[size]).replace("\0", "a");
  }

  @Nested
  class WireMockTests {
    WireMockServer mockBackend;

    @BeforeEach
    void setup() {
      mockBackend = new WireMockServer(wireMockConfig().dynamicPort());
      mockBackend.stubFor(WireMock.post(urlPathMatching("/report")).willReturn(WireMock.ok()));
      mockBackend.start();
    }

    @AfterEach
    void teardown() {
      mockBackend.stop();
    }

    @Test
    void sendMetric() {
      WavefrontClient wfClient = new WavefrontClient.Builder(mockBackend.baseUrl())
          .includeSdkMetrics(false)
          .build();
      long timestamp = System.currentTimeMillis();

      assertDoesNotThrow(() -> {
        wfClient.sendMetric("a-name", 1.0, timestamp, "a-source", new HashMap<>());
        wfClient.flush();
      });

      String expectedBody = "\"a-name\" 1.0 " + timestamp + " source=\"a-source\"\n";
      mockBackend.verify(1, postRequestedFor(urlEqualTo("/report?f=wavefront"))
          .withRequestBody(matching(expectedBody))
          .withHeader("Content-Type", WireMock.equalTo("application/octet-stream")));
    }

    @Test
    void sendSpan() {
      WavefrontClient wfClient = new WavefrontClient.Builder(mockBackend.baseUrl())
          .includeSdkMetrics(false)
          .build();
      long timestamp = System.currentTimeMillis();
      UUID traceId = UUID.fromString("01010101-0101-0101-0101-010101010101");
      UUID spanId = UUID.fromString("00000000-0000-0000-0000-000000000001");

      assertDoesNotThrow(() -> {
        wfClient.sendSpan("a-name", timestamp, 1138, "a-source", traceId, spanId,
            null, null, null, null);
        wfClient.flush();
      });

      String expectedBody = "\"a-name\" source=\"a-source\" traceId=" + traceId + " spanId=" +
          spanId + " " + timestamp + " 1138\n";
      mockBackend.verify(1, postRequestedFor(urlEqualTo("/report?f=trace"))
          .withRequestBody(matching(expectedBody))
          .withHeader("Content-Type", WireMock.equalTo("application/octet-stream")));
    }

    @Test
    void sendSpanWithLogs() {
      WavefrontClient wfClient = new WavefrontClient.Builder(mockBackend.baseUrl())
              .includeSdkMetrics(false)
              .build();
      long timestamp = System.currentTimeMillis();
      UUID traceId = UUID.fromString("01010101-0101-0101-0101-010101010101");
      UUID spanId = UUID.fromString("00000000-0000-0000-0000-000000000001");
      List<Pair<String, String>> tags =
              Collections.singletonList(Pair.of("_spanSecondaryId", "server"));
      SpanLog spanLog =
              new SpanLog(timestamp, Collections.singletonMap("exception", "ClassNotFound"));

      assertDoesNotThrow(() -> {
        wfClient.sendSpan("a-name", timestamp, 1138, "a-source", traceId, spanId,
                          null, null, tags, Collections.singletonList(spanLog));
        wfClient.flush();
      });

      String expectedSpanBody = "\"a-name\" source=\"a-source\" traceId=" + traceId + " spanId=" +
              spanId + " \"_spanSecondaryId\"=\"server\" \"_spanLogs\"=\"true\" " + timestamp +
              " 1138\n";
      mockBackend.verify(1, postRequestedFor(urlEqualTo("/report?f=trace"))
              .withRequestBody(matching(expectedSpanBody))
              .withHeader("Content-Type", WireMock.equalTo("application/octet-stream")));

      mockBackend.verify(1, postRequestedFor(urlEqualTo("/report?f=spanLogs"))
              .withRequestBody(matchingJsonPath("$.traceId", equalTo("01010101-0101-0101-0101-010101010101")))
              .withRequestBody(matchingJsonPath("$.spanId", equalTo("00000000-0000-0000-0000-000000000001")))
              .withRequestBody(matchingJsonPath("$.logs", equalToJson("[{\"timestamp\":" + timestamp + ",\"fields\":{\"exception\":\"ClassNotFound\"}}]")))
              .withRequestBody(matchingJsonPath("$.span", equalTo(expectedSpanBody)))
              .withRequestBody(matchingJsonPath("$._spanSecondaryId", equalTo("server")))
              .withHeader("Content-Type", WireMock.equalTo("application/octet-stream")));
    }

    @Test
    void sendDistribution() {
      WavefrontClient wfClient = new WavefrontClient.Builder(mockBackend.baseUrl())
          .includeSdkMetrics(false)
          .build();
      long timestamp = System.currentTimeMillis();

      assertDoesNotThrow(() -> {
        wfClient.sendDistribution("a-name",
            Collections.singletonList(new Pair<>(1.1, 2)),
            Collections.singleton(HistogramGranularity.MINUTE),
            timestamp, "a-source", null);
        wfClient.flush();
      });

      String expectedBody = "!M " + timestamp + " #2 1.1 \"a-name\" source=\"a-source\"\n";
      mockBackend.verify(1, postRequestedFor(urlEqualTo("/report?f=histogram"))
          .withRequestBody(matching(expectedBody))
          .withHeader("Content-Type", WireMock.equalTo("application/octet-stream")));
    }

    @Test
    void canSendTracesToDifferentPort() {
      WireMockServer mockTraceBackend = new WireMockServer(wireMockConfig().dynamicPort());
      mockTraceBackend.stubFor(WireMock.post(urlPathMatching("/report")).willReturn(WireMock.ok()));
      mockTraceBackend.start();
      assertNotEquals(mockBackend.port(), mockTraceBackend.port());

      WavefrontClient wfClient = new WavefrontClient.Builder(mockBackend.baseUrl())
          .tracesPort(mockTraceBackend.port())
          .includeSdkMetrics(false)
          .build();
      long timestamp = System.currentTimeMillis();

      assertDoesNotThrow(() -> {
        wfClient.sendMetric("a-name", 1.0, timestamp, "a-source", new HashMap<>());
        wfClient.sendSpan("a-name", timestamp, 1138, "a-source",
            UUID.fromString("01010101-0101-0101-0101-010101010101"),
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            null, null, null, null);
        wfClient.flush();
      });

      mockBackend.verify(1, postRequestedFor(urlEqualTo("/report?f=wavefront")));
      mockTraceBackend.verify(1, postRequestedFor(urlEqualTo("/report?f=trace")));

      mockTraceBackend.stop();
    }
  }

  @Nested
  class Builder {

    @Test
    public void tokenServiceClassTest() {
      WavefrontClient wfClient = new WavefrontClient.Builder("", "TOKEN")
              .build();
      assertNotNull(wfClient);
      assertNotNull(wfClient.getTokenService());
      assertEquals(WavefrontTokenService.class.getSimpleName(), wfClient.getTokenService().getClass().getSimpleName());

      wfClient = new WavefrontClient.Builder("")
              .build();
      assertNotNull(wfClient);
      assertNotNull(wfClient.getTokenService());
      assertEquals(NoopProxyTokenService.class.getSimpleName(), wfClient.getTokenService().getClass().getSimpleName());

      wfClient = new WavefrontClient.Builder("", "cspClientId", "cspClientSecret")
              .build();
      assertNotNull(wfClient);
      assertNotNull(wfClient.getTokenService());
      assertEquals(CSPTokenService.class.getSimpleName(), wfClient.getTokenService().getClass().getSimpleName());

      wfClient = new WavefrontClient.Builder("", "TOKEN")
              .useTokenForCSP().build();
      assertNotNull(wfClient);
      assertNotNull(wfClient.getTokenService());
      assertEquals(CSPTokenService.class.getSimpleName(), wfClient.getTokenService().getClass().getSimpleName());
    }

    @Nested
    class ValidateEndpoint {
      @Test
      public void connectsToUrl() {
        ServerSocket fakeServer = assertDoesNotThrow(() -> new ServerSocket(0));
        String url = "http://127.0.0.1:" + fakeServer.getLocalPort();
        WavefrontClient.Builder wfClientBuilder = new WavefrontClient.Builder(url, "token");

        assertDoesNotThrow(wfClientBuilder::validateEndpoint);
        assertDoesNotThrow(fakeServer::close);
      }

      @Test
      public void throwsForNoConnection() {
        String url = "http://127.0.0.1:" + getClosedPort();
        WavefrontClient.Builder wfClientBuilder = new WavefrontClient.Builder(url, "token");

        Exception e = assertThrows(IllegalArgumentException.class,
            wfClientBuilder::validateEndpoint);
        assertEquals("Unable to connect to " + url, e.getMessage());
      }

      @ParameterizedTest
      @NullAndEmptySource
      @ValueSource(strings = {"127.0.0.1:28788", "not a valid endpoint"})
      public void throwsForInvalidUrl(String url) {
        WavefrontClient.Builder wfClientBuilder = new WavefrontClient.Builder(url, "token");

        Exception e = assertThrows(IllegalArgumentException.class,
            wfClientBuilder::validateEndpoint);
        assertEquals(url + " is not a valid url", e.getMessage());
      }

      private int getClosedPort() {
        return assertDoesNotThrow(() -> {
          ServerSocket tmpSocket = new ServerSocket(0);
          tmpSocket.close();
          return tmpSocket.getLocalPort();
        });
      }
    }
  }
}
