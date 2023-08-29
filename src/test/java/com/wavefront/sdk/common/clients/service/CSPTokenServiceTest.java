package com.wavefront.sdk.common.clients.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.LoggedResponse;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.wavefront.sdk.common.clients.service.token.CSPServerTokenURLConnectionFactory;
import com.wavefront.sdk.common.clients.service.token.CSPTokenService;
import com.wavefront.sdk.common.clients.service.token.CSPUserTokenURLConnectionFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CSPTokenServiceTest {
  public static final String SERVER_AUTH_PATH = "/csp/gateway/am/api/auth/authorize";
  public static final String USER_AUTH_PATH = "/csp/gateway/am/api/auth/api-tokens/authorize";
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
    List<LoggedRequest> allUnmatchedRequests = mockBackend.findAllUnmatchedRequests();
    assertTrue(allUnmatchedRequests.isEmpty());
  }

  @Test
  public void testHasDirectIngestScope() {
    final String scopeString = "external/<uuid>/*/aoa:directDataIngestion external/<uuid>/aoa:directDataIngestion csp:org_member";

    assertTrue(CSPTokenService.hasDirectIngestScope(scopeString));
    assertFalse(CSPTokenService.hasDirectIngestScope("no direct data ingestion scope"));
    assertFalse(CSPTokenService.hasDirectIngestScope(""));
    assertFalse(CSPTokenService.hasDirectIngestScope(null));
    assertTrue(CSPTokenService.hasDirectIngestScope("aoa/*"));
    assertTrue(CSPTokenService.hasDirectIngestScope("some aoa/*"));
    assertTrue(CSPTokenService.hasDirectIngestScope("aoa:*"));
    assertTrue(CSPTokenService.hasDirectIngestScope("some aoa:*"));
    assertTrue(CSPTokenService.hasDirectIngestScope("ALL_PERMISSIONS"));
    assertTrue(CSPTokenService.hasDirectIngestScope("aoa:metric ALL_PERMISSIONS aoa:log"));
    assertFalse(CSPTokenService.hasDirectIngestScope("NO_PERMISSIONS"));
  }

  @Test
  void testCSPServerTokenReturnsAccessToken() {
    mockBackend.stubFor(WireMock.post(urlPathMatching(SERVER_AUTH_PATH)).willReturn(WireMock.ok(MOCK_RESPONSE)));
    mockBackend.start();

    CSPTokenService cspTokenService = new CSPTokenService(new CSPServerTokenURLConnectionFactory(mockBackend.baseUrl(), "N/A", "N/A", null));

    assertEquals("accessToken", cspTokenService.getToken());

    mockBackend.verify(
        postRequestedFor(urlEqualTo(SERVER_AUTH_PATH))
            .withRequestBody(equalTo("grant_type=client_credentials"))
    );
  }

  @Test
  void testCSPUserTokenReturnsAccessToken() {
    mockBackend.stubFor(WireMock.post(urlPathMatching(USER_AUTH_PATH)).willReturn(WireMock.ok(MOCK_RESPONSE)));
    mockBackend.start();

    CSPTokenService cspTokenService = new CSPTokenService(new CSPUserTokenURLConnectionFactory(mockBackend.baseUrl(), "aUserToken"));

    assertEquals("accessToken", cspTokenService.getToken());

    mockBackend.verify(
        postRequestedFor(urlEqualTo(USER_AUTH_PATH))
            .withRequestBody(equalTo("grant_type=api_token&refresh_token=aUserToken"))
    );
  }

  @Test
  void testCSPHandlesOrgId() {
    mockBackend.stubFor(WireMock.post(urlPathMatching(SERVER_AUTH_PATH)).willReturn(WireMock.ok(MOCK_RESPONSE)));
    mockBackend.start();

    CSPTokenService cspTokenService = new CSPTokenService(new CSPServerTokenURLConnectionFactory(mockBackend.baseUrl(), "N/A", "N/A", "anOrg"));

    assertEquals("accessToken", cspTokenService.getToken());

    mockBackend.verify(
        postRequestedFor(urlEqualTo(SERVER_AUTH_PATH))
            .withRequestBody(equalTo("grant_type=client_credentials&orgId=anOrg"))
    );
  }

  @Test
  void testCSPMultipleAccessTokens() throws InterruptedException, NoSuchFieldException, IllegalAccessException {
    createWireMockStubWithStates(SERVER_AUTH_PATH, Scenario.STARTED, "second", MOCK_RESPONSE);
    createWireMockStubWithFinalState(SERVER_AUTH_PATH, "second", MOCK_RESPONSE2);
    mockBackend.start();

    CSPTokenService cspTokenService = new CSPTokenService(new CSPServerTokenURLConnectionFactory(mockBackend.baseUrl(), "N/A", "N/A", null));

    Field defaultThreadDelay = CSPTokenService.class.getDeclaredField("DEFAULT_THREAD_DELAY");
    defaultThreadDelay.setAccessible(true);
    defaultThreadDelay.set(cspTokenService, Duration.ofSeconds(1));

    assertEquals("accessToken", cspTokenService.getToken());
    Thread.sleep(2_000);
    assertEquals("accessToken2", cspTokenService.getToken());
  }

  // Note: Server Token Auth returns 401
  @Test
  void testCSPReturns4xx() {
    // 401
    mockBackend.stubFor(WireMock.post(urlPathMatching(SERVER_AUTH_PATH)).willReturn(WireMock.unauthorized()));
    mockBackend.start();

    CSPTokenService cspTokenService = new CSPTokenService(new CSPServerTokenURLConnectionFactory(mockBackend.baseUrl(), "N/A", "N/A", null));
    assertEquals("INVALID_TOKEN", cspTokenService.getToken());
    mockBackend.verify(1, postRequestedFor(urlEqualTo(SERVER_AUTH_PATH)));
    LoggedResponse response = mockBackend.getAllServeEvents().get(0).getResponse();
    assertEquals(401, response.getStatus());

    // 400
    mockBackend.stubFor(WireMock.post(urlPathMatching(USER_AUTH_PATH)).willReturn(WireMock.badRequest()));
    cspTokenService = new CSPTokenService(new CSPUserTokenURLConnectionFactory(mockBackend.baseUrl(), "N/A"));
    assertEquals("INVALID_TOKEN", cspTokenService.getToken());
    mockBackend.verify(1, postRequestedFor(urlEqualTo(USER_AUTH_PATH)));
    response = mockBackend.getAllServeEvents().get(0).getResponse();
    assertEquals(400, response.getStatus());
  }

  @Test
  void testCSPReturns500() {
    mockBackend.stubFor(WireMock.post(urlPathMatching(SERVER_AUTH_PATH)).willReturn(WireMock.serverError()));
    mockBackend.start();

    CSPTokenService cspTokenService = new CSPTokenService(new CSPServerTokenURLConnectionFactory(mockBackend.baseUrl(), "N/A", "N/A", null));
    assertNull(cspTokenService.getToken());
  }

  @Test
  void testCSPConnectionError() {
    mockBackend.stubFor(WireMock.post(urlPathMatching(SERVER_AUTH_PATH)).willReturn(WireMock.serverError().withFixedDelay(5_000)));
    mockBackend.start();

    CSPTokenService cspTokenService = new CSPTokenService(new CSPServerTokenURLConnectionFactory(mockBackend.baseUrl(), "N/A", "N/A", null, 100, 100));
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

  private void createWireMockStubWithFinalState(final String url, final String currentState, final String responseBody) {
    mockBackend.stubFor(post(urlEqualTo(url))
        .inScenario("csp")
        .whenScenarioStateIs(currentState)
        .willReturn(aResponse()
            .withStatus(200)
            .withBody(responseBody)));
  }
}
