package com.wavefront.sdk.common.clients.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.wavefront.sdk.common.clients.service.token.CSPServerToServerTokenService;
import com.wavefront.sdk.common.clients.service.token.CSPTokenService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.wavefront.sdk.common.clients.service.token.CSPServerToServerTokenService.hasDirectIngestScope;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CSPServerToServerTokenServiceTest {

  @Test
  public void testHasDirectIngestScope() {
    final String uuid = UUID.randomUUID().toString();

    final String scopeString = "external/" + uuid + "/*/aoa:directDataIngestion external/" + uuid + "/aoa:directDataIngestion csp:org_member";

    assertTrue(hasDirectIngestScope(scopeString));
    assertFalse(hasDirectIngestScope("no direct data ingestion scope"));
    assertFalse(hasDirectIngestScope(""));
    assertFalse(hasDirectIngestScope(null));
    assertTrue(hasDirectIngestScope("aoa/*"));
    assertTrue(hasDirectIngestScope("some aoa/*"));
    assertTrue(hasDirectIngestScope("aoa:*"));
    assertTrue(hasDirectIngestScope("some aoa:*"));
  }

  @Nested
  class WireMockTests {
    WireMockServer mockBackend;

    private final String MOCK_RESPONSE = "{\"scope\":\"scope aoa/*\",\"id_token\":null,\"token_type\":\"bearer\",\"expires_in\":1,\"access_token\":\"accessToken\",\"refresh_token\":null}\n";
    private final String MOCK_RESPONSE2 = "{\"scope\":\"scope aoa/*\",\"id_token\":null,\"token_type\":\"bearer\",\"expires_in\":1,\"access_token\":\"accessToken2\",\"refresh_token\":null}\n";

    @BeforeEach
    void setup() {
      mockBackend = new WireMockServer(wireMockConfig().dynamicPort());
    }

    @AfterEach
    void teardown() {
      mockBackend.stop();
    }

    @Test
    void testCSPReturnsAccessToken() {
      mockBackend.stubFor(WireMock.post(urlPathMatching("/csp/gateway/am/api/auth/authorize")).willReturn(WireMock.ok(MOCK_RESPONSE)));
      mockBackend.start();

      CSPServerToServerTokenService cspServerToServerTokenService = new CSPServerToServerTokenService(mockBackend.baseUrl(), "N/A", "N/A");
      assertNotNull(cspServerToServerTokenService);
      assertEquals(cspServerToServerTokenService.getToken(), "accessToken");
    }

    @Test
    void testCSPMultipleAccessTokens() throws InterruptedException, NoSuchFieldException, IllegalAccessException {
      createWireMockStubWithStates("/csp/gateway/am/api/auth/authorize", Scenario.STARTED, "second", MOCK_RESPONSE);
      createWireMockStubWithStates("/csp/gateway/am/api/auth/authorize", "second", "third", MOCK_RESPONSE2);
      mockBackend.start();

      CSPServerToServerTokenService cspServerToServerTokenService = new CSPServerToServerTokenService(mockBackend.baseUrl(), "N/A", "N/A");

      Field field = CSPTokenService.class.getDeclaredField("DEFAULT_THREAD_DELAY");;
      field.setAccessible(true);
      field.set(cspServerToServerTokenService, 1);

      assertNotNull(cspServerToServerTokenService);
      assertEquals(cspServerToServerTokenService.getToken(), "accessToken");
      Thread.sleep(2000);
      assertEquals(cspServerToServerTokenService.getToken(), "accessToken2");
    }

    @Test
    void testCSPReturns401() {
      mockBackend.stubFor(WireMock.post(urlPathMatching("/csp/gateway/am/api/auth/authorize")).willReturn(WireMock.unauthorized()));
      mockBackend.start();

      CSPServerToServerTokenService cspServerToServerTokenService = new CSPServerToServerTokenService(mockBackend.baseUrl(), "N/A", "N/A");
      assertEquals(cspServerToServerTokenService.getToken(), "INVALID_TOKEN");
    }

    @Test
    void testCSPReturns500() {
      mockBackend.stubFor(WireMock.post(urlPathMatching("/csp/gateway/am/api/auth/authorize")).willReturn(WireMock.serverError()));
      mockBackend.start();

      CSPServerToServerTokenService cspServerToServerTokenService = new CSPServerToServerTokenService(mockBackend.baseUrl(), "N/A", "N/A");
      assertNull(cspServerToServerTokenService.getToken());
    }

    @Test
    void testCSPConnectionError() {
      mockBackend.stubFor(WireMock.post(urlPathMatching("/csp/gateway/am/api/auth/authorize")).willReturn(WireMock.serverError()));
      mockBackend.setGlobalFixedDelay(5_000);
      mockBackend.start();

      CSPServerToServerTokenService cspServerToServerTokenService = new CSPServerToServerTokenService(mockBackend.baseUrl(), "N/A", "N/A", 100, 100);
      assertNull(cspServerToServerTokenService.getToken());
    }

    private void createWireMockStubWithStates(final String url, final String currentState, final String nextState, final String responseBody) {
      mockBackend.stubFor(post(urlEqualTo(url))
              .inScenario("csp")
              .whenScenarioStateIs(currentState)
              .willSetStateTo(nextState)
              .willReturn(aResponse()
                      .withStatus(200)
                      .withBody(responseBody)));
    }

  }
}