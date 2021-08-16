package com.wavefront.sdk.common.clients;

import com.wavefront.sdk.common.metrics.WavefrontSdkDeltaCounter;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
  class Builder {
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
