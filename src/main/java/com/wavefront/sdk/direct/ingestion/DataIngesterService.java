package com.wavefront.sdk.direct.ingestion;

import com.google.common.io.ByteStreams;
import com.wavefront.sdk.common.clients.WavefrontClientFactory;
import com.wavefront.sdk.common.clients.service.ReportingService;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * DataIngester service that reports entities to Wavefront
 *
 * This class will be removed in future versions in favor of
 *  {@link com.wavefront.sdk.common.clients.WavefrontClientFactory} to construct Proxy and DirectDataIngestion senders.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 * @version $Id: $Id
 */
@Deprecated
public class DataIngesterService implements DataIngesterAPI {

  private static final Logger log = Logger.getLogger(ReportingService.class.getCanonicalName());

  private final String token;
  private final URI uri;
  private static final String BAD_REQUEST = "Bad client request";
  private static final int CONNECT_TIMEOUT_MILLIS = 30000;
  private static final int READ_TIMEOUT_MILLIS = 10000;
  private static final int NO_HTTP_RESPONSE = -1;

  DataIngesterService(String server, String token) {
      this.token = token;
      this.uri = URI.create(server);
  }

  /** {@inheritDoc} */
  @Override
  public int report(String format, InputStream stream) {
    /*
     * Refer https://docs.oracle.com/javase/8/docs/technotes/guides/net/http-keepalive.html
     * for details around why this code is written as it is.
     */
    int statusCode = 400;
    HttpURLConnection urlConn = null;
    try {
      String originalPath = uri.getPath() != null ? uri.getPath() : "";
      URL url = new URL(uri.getScheme(), uri.getHost(), uri.getPort(), originalPath + "/report?f=" + format);
      urlConn = (HttpURLConnection) url.openConnection();
      urlConn.setDoOutput(true);
      urlConn.addRequestProperty("Content-Type", "application/octet-stream");
      urlConn.addRequestProperty("Content-Encoding", "gzip");
      urlConn.addRequestProperty("Authorization", "Bearer " + token);

      urlConn.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
      urlConn.setReadTimeout(READ_TIMEOUT_MILLIS);

      try (GZIPOutputStream gzipOS = new GZIPOutputStream(urlConn.getOutputStream())) {
        ByteStreams.copy(stream, gzipOS);
        gzipOS.flush();
      }
      statusCode = urlConn.getResponseCode();
      readAndClose(urlConn.getInputStream());
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
      log.log(Level.SEVERE, "Unable to obtain status code from the Wavefront service at "
          + urlConn.getURL().toString(), ex);
      statusCode = NO_HTTP_RESPONSE;
    }

    try {
      readAndClose(urlConn.getErrorStream());
    } catch (IOException ex) {
      log.log(Level.SEVERE, "Unable to read and close error stream from the Wavefront service at "
          + urlConn.getURL().toString(), ex);
    }

    return statusCode;
  }

  private void readAndClose(InputStream stream) throws IOException {
    if (stream != null) {
      try (InputStream is = stream) {
        byte[] buffer = new byte[4096];
        // read entire stream before closing
        while (is.read(buffer) > 0) {}
      }
    }
  }
}
