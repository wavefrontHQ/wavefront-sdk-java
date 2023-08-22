package com.wavefront.sdk.common.clients.service.token;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wavefront.sdk.common.NamedThreadFactory;
import com.wavefront.sdk.common.Utils;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public abstract class CSPTokenService implements TokenService, Runnable {


  protected final static int TEN_MINUTES = 600;
  protected final static int THIRTY_SECONDS = 30;
  protected final static int THREE_MINUTES = 180;
  protected static int DEFAULT_THREAD_DELAY = 60;
  // TODO Have logger output correct class name for child classes
  protected static Logger log = Logger.getLogger(CSPTokenService.class.getCanonicalName());
  protected final AtomicBoolean tokenReady = new AtomicBoolean(false);
  protected final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("csp-token-service"));
  protected final ObjectMapper mapper = new ObjectMapper();
  protected int connectTimeoutMillis = 30_000;
  protected int readTimeoutMillis = 10_000;
  protected String cspAccessToken;

  public CSPTokenService() {}

  public CSPTokenService(int connectTimeoutMillis, int readTimeoutMillis) {
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
  }

  public static boolean hasDirectIngestScope(final String scopeList) {
    if (!Utils.isNullOrEmpty(scopeList)) {
      return CSPTokenService.parseScopes(scopeList).stream().anyMatch(s -> s.contains("aoa:directDataIngestion") || s.contains("aoa/*") || s.contains("aoa:*"));
    }

    return false;
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

  abstract protected String getCSPToken();

  protected int getThreadDelay(final int expiresIn) {
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

  private static List<String> parseScopes(final String scope) {
    return Arrays.stream(scope.split("\\s")).collect(Collectors.toList());
  }

  public synchronized void run() {
    this.cspAccessToken = getCSPToken();
  }

  protected String executeAndParseToken(HttpURLConnection urlConn, byte[] postData) throws IOException {
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
}
