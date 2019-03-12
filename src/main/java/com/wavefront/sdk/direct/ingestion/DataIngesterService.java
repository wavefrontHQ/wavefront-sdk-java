package com.wavefront.sdk.direct.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.zip.GZIPOutputStream;

/**
 * DataIngester service that reports entities to Wavefront
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class DataIngesterService implements DataIngesterAPI {
  private final String token;
  private final URI uri;
  private static final String BAD_REQUEST = "Bad client request";
  private static final int CONNECT_TIMEOUT_MILLIS = 30000;
  private static final int READ_TIMEOUT_MILLIS = 10000;

  DataIngesterService(String server, String token) {
      this.token = token;
      this.uri = URI.create(server);
  }

  @Override
  public int report(String format, InputStream stream) throws IOException {
    /*
     * Refer https://docs.oracle.com/javase/8/docs/technotes/guides/net/http-keepalive.html
     * for details around why this code is written as it is.
     */
    int statusCode = 400;
    HttpURLConnection urlConn = null;
    try {
      URL url = new URL(uri.getScheme(), uri.getHost(), uri.getPort(), "/report?f=" + format);
      urlConn = (HttpURLConnection) url.openConnection();
      urlConn.setDoOutput(true);
      urlConn.addRequestProperty("Content-Type", "application/octet-stream");
      urlConn.addRequestProperty("Content-Encoding", "gzip");
      urlConn.addRequestProperty("Authorization", "Bearer " + token);

      urlConn.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
      urlConn.setReadTimeout(READ_TIMEOUT_MILLIS);

      try (GZIPOutputStream gzipOS = new GZIPOutputStream(urlConn.getOutputStream())) {
        // As long as there are bytes remaining to be read, read them in chunks of 4k bytes.
        while (stream.available() > 0) {
          // Allocate only as much as required.
          byte[] buffer = new byte[Math.min(stream.available(), 4096)];
          stream.read(buffer);
          gzipOS.write(buffer);
        }
        gzipOS.flush();
      }
      statusCode = urlConn.getResponseCode();
      readAndClose(urlConn.getInputStream());
    } catch (IOException ex) {
      if (urlConn != null) {
        statusCode = urlConn.getResponseCode();
        readAndClose(urlConn.getErrorStream());
      }
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
