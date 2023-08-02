package com.wavefront.sdk.common.clients.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.wavefront.sdk.common.Constants;
import com.wavefront.sdk.common.Utils;
import com.wavefront.sdk.common.annotation.Nullable;
import com.wavefront.sdk.common.logging.MessageSuppressingLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * ReportingService that reports entities to Proxy or Wavefront services.
 *
 * @author Mike McMahon (mike.mcmahon@wavefront.com)
 * @version $Id: $Id
 */
public class ReportingService implements ReportAPI {

  // This logger is intended to be configurable in the WavefrontClient.Builder. Given that the invoker controls the
  // configuration, this is not a static logger.
  private final MessageSuppressingLogger messageSuppressingLogger;

  // TODO - tests for diff kinds of TokenService
  private final TokenService tokenService;
  private final URI uri;

  private static final int CONNECT_TIMEOUT_MILLIS = 30000;
  private static final int READ_TIMEOUT_MILLIS = 10000;
  private static final int BUFFER_SIZE = 4096;
  private static final int NO_HTTP_RESPONSE = -1;

  /**
   * <p>Constructor for ReportingService.</p>
   *
   * @param uri a {@link java.net.URI} object
   * @param tokenService a {@link TokenService} object
   * @param reportingServiceLogSuppressTimeSeconds a long
   */
  public ReportingService(URI uri, TokenService tokenService, long reportingServiceLogSuppressTimeSeconds) {
    this.uri = uri;
    this.tokenService = tokenService;

    // Setting suppress time to 0 invalidates the cache used by the message suppressing logger and doesn't log anything.
    // So defaulting to the minimum of 1 second.
    reportingServiceLogSuppressTimeSeconds = reportingServiceLogSuppressTimeSeconds <= 0 ? 1 : reportingServiceLogSuppressTimeSeconds;
    this.messageSuppressingLogger = new MessageSuppressingLogger(Logger.getLogger(
            ReportingService.class.getCanonicalName()), reportingServiceLogSuppressTimeSeconds, TimeUnit.SECONDS);
  }

  /** {@inheritDoc} */
  @Override
  public int send(String format, InputStream stream) {
    HttpURLConnection urlConn = null;
    int statusCode = 400;
    try {
      URL url = getReportingUrl(uri, format);
      urlConn = (HttpURLConnection) url.openConnection();
      urlConn.setDoOutput(true);
      urlConn.setRequestMethod("POST");
      urlConn.addRequestProperty("Content-Type", "application/octet-stream");
      urlConn.addRequestProperty("Content-Encoding", "gzip");

      String token = tokenService.getToken();

      if (!Utils.isNullOrEmpty(token)) {
        urlConn.addRequestProperty("Authorization", "Bearer " + token);
      }

      urlConn.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
      urlConn.setReadTimeout(READ_TIMEOUT_MILLIS);

      try (GZIPOutputStream gzipOS = new GZIPOutputStream(urlConn.getOutputStream())) {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (stream.available() > 0) {
          gzipOS.write(buffer, 0, stream.read(buffer));
        }
        gzipOS.flush();
      }
      statusCode = urlConn.getResponseCode();
      readAndClose(urlConn.getInputStream());
      messageSuppressingLogger.reset(urlConn.getURL().toString());
    } catch (IOException ex) {
      if (urlConn != null) {
        return safeGetResponseCodeAndClose(urlConn);
      }
    }
    return statusCode;
  }

  /** {@inheritDoc} */
  @Override
  public int sendEvent(InputStream stream) {
    HttpURLConnection urlConn = null;
    int statusCode = 400;
    try {
      URL url = getEventReportingUrl(uri);
      urlConn = (HttpURLConnection) url.openConnection();
      urlConn.setDoOutput(true);
      urlConn.setRequestMethod("POST");

      String token = tokenService.getToken();

      if (!Utils.isNullOrEmpty(token)) {
        urlConn.addRequestProperty("Authorization", "Bearer " + token);
      }
      urlConn.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
      urlConn.setReadTimeout(READ_TIMEOUT_MILLIS);

      if (uri.getScheme().equals(Constants.HTTP_PROXY_SCHEME)) {
        // Event is in compressed line format for proxy.
        urlConn.addRequestProperty("Content-Type", "application/octet-stream");
        urlConn.addRequestProperty("Content-Encoding", "gzip");
        try (GZIPOutputStream gzipOS = new GZIPOutputStream(urlConn.getOutputStream())) {
          byte[] buffer = new byte[BUFFER_SIZE];
          while (stream.available() > 0) {
            gzipOS.write(buffer, 0, stream.read(buffer));
          }
          gzipOS.flush();
        }
      } else {
        // Event is in uncompressed JSON format for direct ingestion.
        urlConn.addRequestProperty("Content-Type", "application/json");
        try (OutputStream urlOS = urlConn.getOutputStream()) {
          byte[] buffer = new byte[BUFFER_SIZE];
          while (stream.available() > 0) {
            urlOS.write(buffer, 0, stream.read(buffer));
          }
          urlOS.flush();
        }
      }

      statusCode = urlConn.getResponseCode();
      readAndClose(urlConn.getInputStream());
      messageSuppressingLogger.reset(urlConn.getURL().toString());
    } catch (IOException ex) {
      if (urlConn != null) {
        return safeGetResponseCodeAndClose(urlConn);
      }
    }
    return statusCode;
  }

  private int safeGetResponseCodeAndClose(HttpURLConnection urlConn) {
    int statusCode;
    try {
      statusCode = urlConn.getResponseCode();
    } catch (IOException ex) {
      messageSuppressingLogger.log(urlConn.getURL().toString(), Level.SEVERE,
          "Unable to obtain status code from the Wavefront service at "
              + urlConn.getURL().toString() + " due to: " + ex);
      statusCode = NO_HTTP_RESPONSE;
    }

    try {
      readAndClose(urlConn.getErrorStream());
    } catch (IOException ex) {
      messageSuppressingLogger.log(urlConn.getURL().toString(), Level.SEVERE,
          "Unable to read and close error stream from the Wavefront service at "
              + urlConn.getURL().toString() + " due to: " + ex);
    }

    return statusCode;
  }

  private void readAndClose(InputStream stream) throws IOException {
    if (stream != null) {
      try (InputStream is = stream) {
        byte[] buffer = new byte[BUFFER_SIZE];
        // read entire stream before closing
        while (is.read(buffer) > 0) {
        }
      }
    }
  }

  /**
   * For a given URI generate a properly formatted URL suitable
   * for sending data to either proxies or a Wavefront service.
   *
   * @param server a server to report to
   * @param format the format of data to send
   * @return returns as properly formatted URL ending in /report?=format
   * @throws java.net.MalformedURLException throws an Exception
   */
  @VisibleForTesting
  public static URL getReportingUrl(URI server, String format) throws MalformedURLException {
    String originalPath = server.getPath() != null ? server.getPath() : "";
    originalPath = originalPath.replaceAll("(\\/){2,}", "/");
    originalPath = originalPath.equals("/") ? "" : originalPath;
    if (originalPath.endsWith("/report/")) {
      originalPath = originalPath.replaceAll("/report/$", "/report");
    } else if (!originalPath.endsWith("/report")) {
      originalPath += "/report";
    }
    URL url = new URL(server.getScheme(), server.getHost(), server.getPort(), originalPath + "?f=" + format);
    return url;
  }

  /**
   * For a given URI generate a properly formatted URL suitable
   * for sending events to either proxies or a Wavefront service.
   *
   * @param server a server to report to
   * @return returns as properly formatted URL ending in /api/v2/event
   * @throws java.net.MalformedURLException throws an Exception
   */
  @VisibleForTesting
  public static URL getEventReportingUrl(URI server) throws MalformedURLException {
    String originalPath = server.getPath() != null ? server.getPath() : "";
    originalPath = originalPath.replaceAll("(\\/){2,}", "/");
    originalPath = originalPath.equals("/") ? "" : originalPath;
    if (originalPath.endsWith("/api/v2/event/")) {
      originalPath = originalPath.replaceAll("\\/api\\/v2\\/event\\/$", "/api/v2/event");
    } else if (!originalPath.endsWith("/api/v2/event")) {
      originalPath += "/api/v2/event";
    }
    URL url = new URL(server.getScheme(), server.getHost(), server.getPort(), originalPath);
    return url;
  }
}
