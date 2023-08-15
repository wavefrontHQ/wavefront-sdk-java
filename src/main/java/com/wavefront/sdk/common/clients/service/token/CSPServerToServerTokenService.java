package com.wavefront.sdk.common.clients.service.token;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wavefront.sdk.common.NamedThreadFactory;
import com.wavefront.sdk.common.Utils;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CSPServerToServerTokenService implements TokenService, Runnable {
  private static final Logger log = Logger.getLogger(CSPServerToServerTokenService.class.getCanonicalName());

  private final static String OAUTH_PATH = "/csp/gateway/am/api/auth/authorize";
  private final static int TEN_MINUTES = 600;
  private final static int THIRTY_SECONDS = 30;
  private final static int THREE_MINUTES = 180;
  private static int DEFAULT_THREAD_DELAY = 60;

  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("csp-server-to-server-token-service"));
  private final ObjectMapper mapper = new ObjectMapper();
  private final AtomicBoolean tokenReady = new AtomicBoolean(false);

  private final String cspBaseURL;
  private final String cspClientId;
  private final String cspClientSecret;

  private final int connectTimeoutMillis;
  private final int readTimeoutMillis;
  private String cspAccessToken;

  public CSPServerToServerTokenService(final String cspBaseURL, final String cspClientId, final String cspClientSecret) {
    this.cspBaseURL = cspBaseURL;
    this.cspClientId = cspClientId;
    this.cspClientSecret = cspClientSecret;
    this.connectTimeoutMillis = 30_000;
    this.readTimeoutMillis = 10_000;
  }

  public CSPServerToServerTokenService(final String cspBaseURL, final String cspClientId, final String cspClientSecret, final int connectTimeoutMillis, final int readTimeoutMillis) {
    this.cspBaseURL = cspBaseURL;
    this.cspClientId = cspClientId;
    this.cspClientSecret = cspClientSecret;
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
  }

  @Override
  public synchronized String getToken() {
    // First access gets the token and is blocking, which schedules the next token fetch.
    if (!tokenReady.get()) {
      run();
      tokenReady.set(true);
    }

    return cspAccessToken;
  }

  private String getCSPToken() {
    HttpURLConnection urlConn = null;

    log.info("Attempting to get a CSP token");

    final String urlParameters = "grant_type=client_credentials";
    final byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);

    try {
      final URL url = new URL(cspBaseURL + OAUTH_PATH);
      urlConn = (HttpURLConnection) url.openConnection();
      urlConn.setDoOutput(true);
      urlConn.setRequestMethod("POST");
      urlConn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      urlConn.addRequestProperty("Authorization", "Basic " + buildHttpBasicToken(cspClientId, cspClientSecret));
      urlConn.setRequestProperty("Content-Length", Integer.toString(postData.length));

      urlConn.setConnectTimeout(connectTimeoutMillis);
      urlConn.setReadTimeout(readTimeoutMillis);

      //Send request
      final DataOutputStream wr = new DataOutputStream(urlConn.getOutputStream());
      wr.write(postData);
      wr.flush();
      wr.close();

      final int statusCode = urlConn.getResponseCode();

      if (statusCode == 200) {
        try {
          final CSPAuthorizeResponse parsedResponse = mapper.readValue(urlConn.getInputStream(), CSPAuthorizeResponse.class);

          log.info("A CSP token has been received.");

          if (!hasDirectIngestScope(parsedResponse.scope)) {
            log.warning("The CSP response did not find any scope matching 'aoa:directDataIngestion' which is required for Wavefront direct ingestion.");
          }

          log.info("A CSP token has been received.");

          // Schedule token refresh in the future
          int threadDelay = getThreadDelay(parsedResponse.expiresIn);

          log.info("Will schedule the CSP token to be refreshed in: " + threadDelay + " seconds");

          executor.schedule(this, threadDelay, TimeUnit.SECONDS);

          return parsedResponse.accessToken;
        } catch (JsonProcessingException e) {
          log.info("The request to CSP returned invalid json. Please restart your app.");

          return "INVALID_TOKEN";
        }
      } else {
        log.info("The request to CSP returned: " + statusCode);

        if (statusCode >= 500 && statusCode < 600) {
          log.info("The Wavefront SDK will try to reauthenticate with CSP on the next request.");
          tokenReady.set(false);

          return null;
        }

        // Anything not 5xx will return INVALID_TOKEN
        return "INVALID_TOKEN";
      }

    } catch (IOException ex) {
      // Connection Problem
      log.warning("Error connecting to CSP: " + ex.getLocalizedMessage());

      log.info("The Wavefront SDK will try to reauthenticate with CSP on the next request.");
      tokenReady.set(false);

      return null;
    }
  }

  private int getThreadDelay(final int expiresIn) {
    int retVal;

    if (expiresIn < TEN_MINUTES) {
      retVal = expiresIn - THIRTY_SECONDS;
    } else {
      retVal = expiresIn - THREE_MINUTES;
    }

    if (retVal <= 0) {
      retVal = DEFAULT_THREAD_DELAY;
    }

    return retVal;
  }

  public synchronized void run() {
    this.cspAccessToken = getCSPToken();
  }

  private static List<String> parseScopes(final String scope) {
    return Arrays.stream(scope.split("\\s")).collect(Collectors.toList());
  }

  public static boolean hasDirectIngestScope(final String scopeList) {
    if (!Utils.isNullOrEmpty(scopeList)) {
      return parseScopes(scopeList).stream().anyMatch(s -> s.contains("aoa:directDataIngestion") || s.contains("aoa/*") || s.contains("aoa:*"));
    }

    return false;
  }

  private String buildHttpBasicToken(final String cspClientId, final String cspClientSecret) {
    final String encodeMe = cspClientId + ":" + cspClientSecret;
    return Base64.getEncoder().encodeToString(encodeMe.getBytes());
  }
}
