package com.wavefront.sdk.common.clients.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wavefront.sdk.common.NamedThreadFactory;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CSPServerToServerTokenService implements TokenService {
  private final static String OAUTH_PATH = "/csp/gateway/am/api/auth/authorize";
  private final static int CONNECT_TIMEOUT = 30_000;
  private final static int READ_TIMEOUT = 10_000;
  private final static int TEN_MINUTES = 600;
  private final static int THIRTY_SECONDS = 30;
  private final static int THREE_MINUTES = 180;

  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("csp-server-to-server-token-service"));
  private final ObjectMapper mapper = new ObjectMapper();

  private final String cspBaseURL;
  private final String cspClientId;
  private final String cspClientSecret;

  private String cspAccessToken;
  private boolean firstRun = false;

  public CSPServerToServerTokenService(final String cspBaseURL, final String cspClientId, final String cspClientSecret) {
    this.cspBaseURL = cspBaseURL;
    this.cspClientId = cspClientId;
    this.cspClientSecret = cspClientSecret;
  }

  // TODO - schedule threads?
  // TODO - error handling
  // TODO - tests

  @Override
  public String getToken() {
    // First access gets the token and is blocking, which schedules the next token fetch.
    if (!firstRun) {
      run();
      firstRun = true;
    }

    return cspAccessToken;
  }

  // TODO - CLEANUP
  private String getCSPToken() {
    HttpURLConnection urlConn = null;

    final String urlParameters  = "grant_type=client_credentials";
    final byte[] postData = urlParameters.getBytes( StandardCharsets.UTF_8 );

    try {
      final URL url = new URL(cspBaseURL + OAUTH_PATH);
      urlConn = (HttpURLConnection) url.openConnection();
      urlConn.setDoOutput(true);
      urlConn.setRequestMethod("POST");
      urlConn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      urlConn.addRequestProperty("Authorization", "Basic " + buildHttpBasicToken(cspClientId, cspClientSecret));
      urlConn.setRequestProperty("Content-Length", Integer.toString(postData.length));

      urlConn.setConnectTimeout(CONNECT_TIMEOUT);
      urlConn.setReadTimeout(READ_TIMEOUT);

      //Send request
      final DataOutputStream wr = new DataOutputStream (urlConn.getOutputStream());
      wr.write(postData);
      wr.flush();
      wr.close();

      final int statusCode = urlConn.getResponseCode();

      if (statusCode == 200) {
        try {
          final CSPResponse parsedResponse = mapper.readValue(urlConn.getInputStream(), CSPResponse.class);

          System.out.println(parsedResponse.accessToken);
          System.out.println(parsedResponse.scope);
          System.out.println(parsedResponse.expiresIn);

          executor.schedule(this::run, getThreadDelay(parsedResponse.expiresIn), TimeUnit.SECONDS);

          return parsedResponse.accessToken;
        } catch (JsonProcessingException e) {
          e.printStackTrace();

          return null;
        }
      }

    } catch (IOException ex) {
      System.out.println("WOOOA SAD");
      if (urlConn != null) {
        // return safeGetResponseCodeAndClose(urlConn);
      }
    }

    System.out.println("WOOOA NOOOO");
    return null;
  }

  private int getThreadDelay(final int expiresIn) {
    // TODO - what times make the most sense?
    if (expiresIn < TEN_MINUTES) {
      return expiresIn - THIRTY_SECONDS;
    }

    return expiresIn - THREE_MINUTES;
  }

  private void run() {
    this.cspAccessToken = getCSPToken();
  }

  private String buildHttpBasicToken(final String cspClientId, final String cspClientSecret) {
    final String encodeMe = cspClientId + ":" + cspClientSecret;
    return Base64.getEncoder().encodeToString(encodeMe.getBytes());
  }
}
