package com.wavefront.sdk.common.clients.service.token;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class CSPServerToServerTokenService extends CSPTokenService implements Runnable {
  private static final Logger log = Logger.getLogger(CSPServerToServerTokenService.class.getCanonicalName());
  private final static String OAUTH_PATH = "/csp/gateway/am/api/auth/authorize";

  private final String cspBaseURL;
  private final String cspClientId;
  private final String cspClientSecret;

  public CSPServerToServerTokenService(final String cspBaseURL, final String cspClientId, final String cspClientSecret) {
    this.cspBaseURL = cspBaseURL;
    this.cspClientId = cspClientId;
    this.cspClientSecret = cspClientSecret;
  }

  public CSPServerToServerTokenService(final String cspBaseURL, final String cspClientId, final String cspClientSecret, final int connectTimeoutMillis, final int readTimeoutMillis) {
    super(connectTimeoutMillis, readTimeoutMillis);
    this.cspBaseURL = cspBaseURL;
    this.cspClientId = cspClientId;
    this.cspClientSecret = cspClientSecret;
  }

  protected String getCSPToken() {
    HttpURLConnection urlConn = null;

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

      return executeAndParseToken(urlConn, postData);

    } catch (IOException ex) {
      // Connection Problem
      log.warning("Error connecting to CSP: " + ex.getLocalizedMessage());

      log.info("The Wavefront SDK will try to reauthenticate with CSP on the next request.");
      tokenReady.set(false);

      return null;
    }
  }

  private String executeAndParseToken(HttpURLConnection urlConn, byte[] postData) throws IOException {
    //Send request
    final DataOutputStream wr = new DataOutputStream(urlConn.getOutputStream());
    wr.write(postData);
    wr.flush();
    wr.close();

    final int statusCode = urlConn.getResponseCode();

    if (statusCode == 200) {
      try {
        final CSPAuthorizeResponse parsedResponse = mapper.readValue(urlConn.getInputStream(), CSPAuthorizeResponse.class);

        if (!hasDirectIngestScope(parsedResponse.scope)) {
          log.warning("The CSP response did not find any scope matching 'aoa:directDataIngestion' which is required for Wavefront direct ingestion.");
        }

        // Schedule token refresh in the future
        int threadDelay = getThreadDelay(parsedResponse.expiresIn);

        log.info("A CSP token has been received. Will schedule the CSP token to be refreshed in: " + threadDelay + " seconds");

        executor.schedule(this, threadDelay, TimeUnit.SECONDS);

        return parsedResponse.accessToken;
      } catch (JsonProcessingException e) {
        log.severe("The request to CSP returned invalid json. Please restart your app.");

        return "INVALID_TOKEN";
      }
    } else {
      log.severe("The request to CSP for a token failed with HTTP code: " + statusCode + ".");

      if (statusCode >= 500 && statusCode < 600) {
        log.info("The Wavefront SDK will try to reauthenticate with CSP on the next request.");
        tokenReady.set(false);

        return null;
      }

      // Anything not 5xx will return INVALID_TOKEN
      return "INVALID_TOKEN";
    }
  }

  private String buildHttpBasicToken(final String cspClientId, final String cspClientSecret) {
    final String encodeMe = cspClientId + ":" + cspClientSecret;
    return Base64.getEncoder().encodeToString(encodeMe.getBytes());
  }
}
