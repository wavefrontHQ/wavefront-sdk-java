package com.wavefront.sdk.common.clients.service.token;

import com.google.common.annotations.VisibleForTesting;

import com.wavefront.sdk.common.Utils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CSPServerTokenURLConnectionFactory implements CSPURLConnectionFactory {
  private final static String OAUTH_PATH = "/csp/gateway/am/api/auth/authorize";
  private final static String TYPE = "CSP Client Credentials";

  private final String cspBaseURL;
  private final String cspClientId;
  private final String cspClientSecret;
  private final byte[] postData;
  private int connectTimeoutMillis = 30_000;
  private int readTimeoutMillis = 10_000;

  @VisibleForTesting
  public static Map<CredentialPart, String> parseClientCredentials(String compoundCredentials) {
    // Do NOT include original input in exception or logs, as it contains sensitive credentials.
    IllegalArgumentException ex = new IllegalArgumentException("CSP Client Credentials must be a comma-delimited string of clientId, clientSecret, and an optional orgId.");
    Map<CredentialPart, String> parsedCreds = new HashMap<>();

    String[] parts = compoundCredentials.split(",");
    if (parts.length < 2 || parts.length > 3) {
      throw ex;
    }

    Pattern p = Pattern.compile("(clientId|clientSecret|orgId)=(\\\"|'?)(.+)(\\2)", Pattern.CASE_INSENSITIVE);
    for (String part : parts) {
      Matcher m = p.matcher(part.trim());
      if (!m.matches() || m.groupCount() != 4) {
        throw ex;
      }
      switch (m.group(1).toLowerCase()) {
        case "clientid":
          parsedCreds.put(CredentialPart.CLIENT_ID, m.group(3));
          break;
        case "clientsecret":
          parsedCreds.put(CredentialPart.CLIENT_SECRET, m.group(3));
          break;
        case "orgid":
          parsedCreds.put(CredentialPart.ORG_ID, m.group(3));
          break;
      }
    }

    return parsedCreds;
  }

  public CSPServerTokenURLConnectionFactory(String cspBaseURL, String cspClientId, String cspClientSecret, String cspOrgId) {
    this.cspBaseURL = cspBaseURL;
    this.cspClientId = cspClientId;
    this.cspClientSecret = cspClientSecret;
    String postData = "grant_type=client_credentials";
    if (!Utils.isNullOrEmpty(cspOrgId)) {
      postData += "&orgId=" + cspOrgId;
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
  public String getType() {
    return TYPE;
  }

  public enum CredentialPart {
    CLIENT_ID,
    CLIENT_SECRET,
    ORG_ID
  }
}
