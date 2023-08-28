package com.wavefront.sdk.common.clients.service.token;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.wavefront.sdk.common.Utils;

public class CSPServerTokenURLConnectionFactory implements CSPURLConnectionFactory {
  private final static String OAUTH_PATH = "/csp/gateway/am/api/auth/authorize";
  private final static String TYPE = "CSP OAUTH";

  private final String cspBaseURL;
  private final String cspClientId;
  private final String cspClientSecret;
  private final String cspOrgId;
  private final byte[] postData;
  private int connectTimeoutMillis = 30_000;
  private int readTimeoutMillis = 10_000;

  public CSPServerTokenURLConnectionFactory(String cspBaseURL, String cspClientId, String cspClientSecret, String cspOrgId) {
    this.cspBaseURL = cspBaseURL;
    this.cspClientId = cspClientId;
    this.cspClientSecret = cspClientSecret;
    this.cspOrgId = cspOrgId;
    String postData = "grant_type=client_credentials";
    if (!Utils.isNullOrEmpty(cspOrgId)) {
      postData += "&orgId="+cspOrgId;
    }
    this.postData = postData.getBytes(StandardCharsets.UTF_8);
  }

  public CSPServerTokenURLConnectionFactory(String cspBaseURL, String cspClientId, String cspClientSecret, String cspOrgId, int connectTimeoutMillis, int readTimeoutMillis) {
    this(cspBaseURL, cspClientId, cspClientSecret, cspOrgId);
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
  }

  @Override
  public HttpURLConnection build() throws IOException {
    URL url = new URL(cspBaseURL + OAUTH_PATH);
    HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();

    urlConn.setDoOutput(true);
    urlConn.setConnectTimeout(connectTimeoutMillis);
    urlConn.setReadTimeout(readTimeoutMillis);

    urlConn.setRequestMethod("POST");
    urlConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    urlConn.setRequestProperty("Accept", "application/json");
    urlConn.setRequestProperty("Content-Length", Integer.toString(postData.length));
    urlConn.setRequestProperty("Authorization", "Basic " + buildHttpBasicToken(cspClientId, cspClientSecret));

    return urlConn;
  }

  @Override
  public byte[] getPostData() {
    return postData;
  }

  private String buildHttpBasicToken(final String cspClientId, final String cspClientSecret) {
    final String encodeMe = cspClientId + ":" + cspClientSecret;
    return Base64.getEncoder().encodeToString(encodeMe.getBytes());
  }

  @Override
  public String getType() { return TYPE; }
}
