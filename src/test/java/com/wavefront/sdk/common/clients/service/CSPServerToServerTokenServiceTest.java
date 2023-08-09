package com.wavefront.sdk.common.clients.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.wavefront.sdk.common.clients.service.token.CSPServerToServerTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

class CSPServerToServerTokenServiceTest {

  @Nested
  class WireMockTests {
    WireMockServer mockBackend;

    private final String MOCK_RESPONSE = "{\"scope\":\"scope\",\"id_token\":null,\"token_type\":\"bearer\",\"expires_in\":1799,\"access_token\":\"accessToken\",\"refresh_token\":null}\n";

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
  }
}