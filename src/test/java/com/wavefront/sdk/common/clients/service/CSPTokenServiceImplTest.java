package com.wavefront.sdk.common.clients.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.wavefront.sdk.common.clients.service.token.CSPServerTokenURLConnectionFactory;
import com.wavefront.sdk.common.clients.service.token.CSPTokenServiceImpl;
import com.wavefront.sdk.common.clients.service.token.CSPUserTokenURLConnectionFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CSPTokenServiceImplTest {

  @Nested
  class CSPUserTokenTests {
    public static final String AUTH_PATH = "/csp/gateway/am/api/auth/api-tokens/authorize";
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
      mockBackend.stubFor(WireMock.post(urlPathMatching(AUTH_PATH)).willReturn(WireMock.ok(MOCK_RESPONSE)));
      mockBackend.start();

      CSPTokenServiceImpl tokenService = new CSPTokenServiceImpl(new CSPUserTokenURLConnectionFactory(mockBackend.baseUrl(), "N/A"));
      assertNotNull(tokenService);
      assertEquals("accessToken", tokenService.getToken());
    }

    @Test
    void testCSPMultipleAccessTokens() throws InterruptedException, NoSuchFieldException, IllegalAccessException {
      createWireMockStubWithStates(AUTH_PATH, Scenario.STARTED, "second", MOCK_RESPONSE);
      createWireMockStubWithStates(AUTH_PATH, "second", "third", MOCK_RESPONSE2);
      mockBackend.start();

      CSPTokenServiceImpl cspTokenService = new CSPTokenServiceImpl(new CSPUserTokenURLConnectionFactory(mockBackend.baseUrl(), "N/A"));

      Field defaultThreadDelay = CSPTokenServiceImpl.class.getDeclaredField("DEFAULT_THREAD_DELAY");
      defaultThreadDelay.setAccessible(true);
      defaultThreadDelay.set(cspTokenService, Duration.ofSeconds(1));

      assertNotNull(cspTokenService);
      assertEquals("accessToken", cspTokenService.getToken());
      Thread.sleep(2_000);
      assertEquals("accessToken2", cspTokenService.getToken());
    }

    // Note: User Token Auth returns 400 as opposed to ServerToServer which returns a 401
    @Test
    void testCSPReturns400() {
      mockBackend.stubFor(WireMock.post(urlPathMatching(AUTH_PATH)).willReturn(WireMock.badRequest()));
      mockBackend.start();

      CSPTokenServiceImpl cspTokenService = new CSPTokenServiceImpl(new CSPUserTokenURLConnectionFactory(mockBackend.baseUrl(), "N/A"));
      assertEquals("INVALID_TOKEN", cspTokenService.getToken());
    }

    @Test
    void testCSPReturns500() {
      mockBackend.stubFor(WireMock.post(urlPathMatching(AUTH_PATH)).willReturn(WireMock.serverError()));
      mockBackend.start();

      CSPTokenServiceImpl cspTokenService = new CSPTokenServiceImpl(new CSPUserTokenURLConnectionFactory(mockBackend.baseUrl(), "N/A"));
      assertNull(cspTokenService.getToken());
    }

    @Test
    void testCSPConnectionError() {
      mockBackend.stubFor(WireMock.post(urlPathMatching(AUTH_PATH)).willReturn(WireMock.serverError()));
      mockBackend.setGlobalFixedDelay(5_000);
      mockBackend.start();

      CSPTokenServiceImpl cspTokenService = new CSPTokenServiceImpl(new CSPUserTokenURLConnectionFactory(mockBackend.baseUrl(), "N/A", 100, 100));
      assertNull(cspTokenService.getToken());
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

  @Nested
  class CSPServerTokenTests {
    public static final String AUTH_PATH = "/csp/gateway/am/api/auth/authorize";
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
      mockBackend.stubFor(WireMock.post(urlPathMatching(AUTH_PATH)).willReturn(WireMock.ok(MOCK_RESPONSE)));
      mockBackend.start();

      CSPTokenServiceImpl cspTokenService = new CSPTokenServiceImpl(new CSPServerTokenURLConnectionFactory(mockBackend.baseUrl(), "N/A", "N/A"));
      assertNotNull(cspTokenService);
      assertEquals("accessToken", cspTokenService.getToken());
    }

    @Test
    void testCSPMultipleAccessTokens() throws InterruptedException, NoSuchFieldException, IllegalAccessException {
      createWireMockStubWithStates(AUTH_PATH, Scenario.STARTED, "second", MOCK_RESPONSE);
      createWireMockStubWithStates(AUTH_PATH, "second", "third", MOCK_RESPONSE2);
      mockBackend.start();

      CSPTokenServiceImpl cspTokenService = new CSPTokenServiceImpl(new CSPServerTokenURLConnectionFactory(mockBackend.baseUrl(), "N/A", "N/A"));

      Field defaultThreadDelay = CSPTokenServiceImpl.class.getDeclaredField("DEFAULT_THREAD_DELAY");
      defaultThreadDelay.setAccessible(true);
      defaultThreadDelay.set(cspTokenService, Duration.ofSeconds(1));

      assertNotNull(cspTokenService);
      assertEquals("accessToken", cspTokenService.getToken());
      Thread.sleep(2_000);
      assertEquals("accessToken2", cspTokenService.getToken());
    }

    // Note: Server Token Auth returns 401
    @Test
    void testCSPReturns401() {
      mockBackend.stubFor(WireMock.post(urlPathMatching(AUTH_PATH)).willReturn(WireMock.unauthorized()));
      mockBackend.start();

      CSPTokenServiceImpl cspTokenService = new CSPTokenServiceImpl(new CSPServerTokenURLConnectionFactory(mockBackend.baseUrl(), "N/A", "N/A"));
      assertEquals("INVALID_TOKEN", cspTokenService.getToken());
    }

    @Test
    void testCSPReturns500() {
      mockBackend.stubFor(WireMock.post(urlPathMatching(AUTH_PATH)).willReturn(WireMock.serverError()));
      mockBackend.start();

      CSPTokenServiceImpl cspTokenService = new CSPTokenServiceImpl(new CSPServerTokenURLConnectionFactory(mockBackend.baseUrl(), "N/A", "N/A"));
      assertNull(cspTokenService.getToken());
    }

    @Test
    void testCSPConnectionError() {
      mockBackend.stubFor(WireMock.post(urlPathMatching(AUTH_PATH)).willReturn(WireMock.serverError()));
      mockBackend.setGlobalFixedDelay(5_000);
      mockBackend.start();

      CSPTokenServiceImpl cspTokenService = new CSPTokenServiceImpl(new CSPServerTokenURLConnectionFactory(mockBackend.baseUrl(), "N/A", "N/A", 100, 100));
      assertNull(cspTokenService.getToken());
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