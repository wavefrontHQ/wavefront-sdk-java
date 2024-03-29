package com.wavefront.sdk.common.clients.service.token;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wavefront.sdk.common.NamedThreadFactory;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class CSPTokenService implements TokenService, Runnable {
  private static final String INVALID_TOKEN = "INVALID_TOKEN";
  private static final Logger log = Logger.getLogger(CSPTokenService.class.getCanonicalName());
  private final AtomicBoolean tokenReady = new AtomicBoolean(false);
  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("csp-token-service"));
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
  private String cspAccessToken;
  private CSPURLConnectionFactory cspUrlConnectionFactory;
  protected static Duration DEFAULT_THREAD_DELAY = Duration.ofSeconds(60);

  public CSPTokenService(CSPURLConnectionFactory cspUrlConnection) {
    this.cspUrlConnectionFactory = cspUrlConnection;
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

  @Override
  public Type getType() {
    return cspUrlConnectionFactory.getTokenType();
  }

  public synchronized void run() {
    this.cspAccessToken = getCSPToken();
  }

  protected String getCSPToken() {
    try {
      HttpURLConnection urlConn = cspUrlConnectionFactory.build();

      final DataOutputStream wr = new DataOutputStream(urlConn.getOutputStream());
      wr.write(cspUrlConnectionFactory.getPostData());
      wr.flush();
      wr.close();

      final int statusCode = urlConn.getResponseCode();

      if (statusCode == 200) {
        try {
          final CSPAuthorizeResponse parsedResponse = mapper.readValue(urlConn.getInputStream(), CSPAuthorizeResponse.class);

          // Schedule token refresh in the future
          Duration threadDelay = getThreadDelay(parsedResponse.expiresIn);
          log.info("Received CSP token and will refresh in " + threadDelay.getSeconds() + " seconds.");
          executor.schedule(this, threadDelay.getSeconds(), TimeUnit.SECONDS);

          return parsedResponse.accessToken;
        } catch (JsonProcessingException e) {
          log.severe("The request to CSP returned invalid json. Please restart your app.");

          return INVALID_TOKEN;
        }
      } else {
        log.severe("The request to CSP for a token failed with HTTP " + statusCode + ".");

        if (statusCode >= 500 && statusCode < 600) {
          log.info("The Wavefront SDK will try to reauthenticate with CSP on the next request.");
          tokenReady.set(false);

          return null;
        }

        // Anything not 5xx will return INVALID_TOKEN
        return INVALID_TOKEN;
      }

    } catch (IOException ex) {
      // Connection Problem
      log.warning("Error connecting to CSP: " + ex.getLocalizedMessage());

      log.info("The Wavefront SDK will try to reauthenticate with CSP on the next request.");
      tokenReady.set(false);

      return null;
    }
  }

  protected Duration getThreadDelay(final Duration expiresIn) {
    Duration delay;

    if (expiresIn.compareTo(Duration.ofMinutes(10)) < 0) {
      delay = expiresIn.minusSeconds(30);
    } else {
      delay = expiresIn.minusMinutes(3);
    }

    if (delay.isZero() || delay.isNegative()) {
      delay = DEFAULT_THREAD_DELAY;
    }

    return delay;
  }
}
