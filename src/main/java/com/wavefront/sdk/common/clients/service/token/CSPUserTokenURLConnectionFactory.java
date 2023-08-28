package com.wavefront.sdk.common.clients.service.token;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class CSPUserTokenURLConnectionFactory implements CSPURLConnectionFactory {
  private final static String OAUTH_PATH = "/csp/gateway/am/api/auth/api-tokens/authorize";
  private final static String TYPE = "CSP API TOKEN";

  private final String cspBaseURL;
  private final byte[] postData;
  private int connectTimeoutMillis = 30_000;
  private int readTimeoutMillis = 10_000;

  public CSPUserTokenURLConnectionFactory(String cspBaseURL, String apiToken) {
    this.cspBaseURL = cspBaseURL;
    this.postData = ("grant_type=api_token&refresh_token=" + apiToken).getBytes(StandardCharsets.UTF_8);
  }

  public CSPUserTokenURLConnectionFactory(String cspBaseURL, String apiToken, int connectTimeoutMillis, int readTimeoutMillis) {
    this(cspBaseURL, apiToken);
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

    return urlConn;
  }

  @Override
  public byte[] getPostData() {
    return postData;
  }

  @Override
  public String getType() { return TYPE; }
}
