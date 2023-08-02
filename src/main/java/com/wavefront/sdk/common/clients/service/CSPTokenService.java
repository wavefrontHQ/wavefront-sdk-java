package com.wavefront.sdk.common.clients.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CSPTokenService implements TokenService {

  private final String OAUTH_PATH = "/csp/gateway/am/api/auth/authorize";
  private final ObjectMapper mapper = new ObjectMapper();

  private final String cspBaseURL;
  private final String cspClientId;
  private final String cspClientSecret;

  public CSPTokenService(final String cspBaseURL, final String cspClientId, final String cspClientSecret) {
    this.cspBaseURL = cspBaseURL;
    this.cspClientId = cspClientId;
    this.cspClientSecret = cspClientSecret;
  }

  // TODO - schedule threads?
  // TODO - cache token ish etc?
  // TODO - error handling
  // TODO - tests

  // TODO - CLEANUP
  private String getCSPToken() {
    HttpURLConnection urlConn = null;

    String urlParameters  = "grant_type=client_credentials";
    byte[] postData = urlParameters.getBytes( StandardCharsets.UTF_8 );

    try {
      URL url = new URL(cspBaseURL + OAUTH_PATH);
      urlConn = (HttpURLConnection) url.openConnection();
      urlConn.setDoOutput(true);
      urlConn.setRequestMethod("POST");
      urlConn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      urlConn.addRequestProperty("Authorization", "Basic " + buildHttpBasicToken(cspClientId, cspClientSecret));
      urlConn.setRequestProperty("Content-Length", Integer.toString(postData.length));

      urlConn.setConnectTimeout(30_000);
      urlConn.setReadTimeout(10_000);

      //Send request
      DataOutputStream wr = new DataOutputStream (urlConn.getOutputStream());
      wr.write(postData);
      wr.flush();
      wr.close();

      int statusCode = urlConn.getResponseCode();

      if (statusCode == 200) {
        // final String response = readAndClose(urlConn.getInputStream());

        try {
          final CSPResponse parsedResponse = mapper.readValue(urlConn.getInputStream(), CSPResponse.class);

          System.out.println(parsedResponse.accessToken);

          return parsedResponse.accessToken;
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        }
      }

    } catch (IOException ex) {
      if (urlConn != null) {
        // return safeGetResponseCodeAndClose(urlConn);
      }
    }
    return null;
  }

  private String buildHttpBasicToken(final String cspClientId, final String cspClientSecret) {
    String encodeMe = cspClientId + ":" + cspClientSecret;
    return Base64.getEncoder().encodeToString(encodeMe.getBytes());
  }

  @Override
  public String getToken() {
    return getCSPToken();
  }
}
