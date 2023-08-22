package com.wavefront.sdk.common.clients.service.token;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CSPUserTokenService extends CSPTokenService {
//  log = Logger.getLogger(CSPServerToServerTokenService.class.getCanonicalName());
  private final static String OAUTH_PATH = "/csp/gateway/am/api/auth/api-tokens/authorize";

  private final String cspBaseURL;
  private final String apiToken;

  public CSPUserTokenService(final String cspBaseURL, final String apiToken) {
    this.cspBaseURL = cspBaseURL;
    this.apiToken = apiToken;
  }

  public CSPUserTokenService(final String cspBaseURL, final String apiToken, final int connectTimeoutMillis, final int readTimeoutMillis) {
    super(connectTimeoutMillis, readTimeoutMillis);
    this.cspBaseURL = cspBaseURL;
    this.apiToken = apiToken;
  }

  protected String getCSPToken() {
    HttpURLConnection urlConn = null;

    final String urlParameters = "grant_type=api_token&refresh_token="+apiToken;
    final byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);

    try {
      final URL url = new URL(cspBaseURL + OAUTH_PATH);
      urlConn = (HttpURLConnection) url.openConnection();
      urlConn.setDoOutput(true);
      urlConn.setRequestMethod("POST");
      urlConn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      urlConn.addRequestProperty("Accept", "application/json");
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

  private String buildHttpBasicToken(final String cspClientId, final String cspClientSecret) {
    final String encodeMe = cspClientId + ":" + cspClientSecret;
    return Base64.getEncoder().encodeToString(encodeMe.getBytes());
  }
}
