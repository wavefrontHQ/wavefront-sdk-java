package com.wavefront.sdk.common.clients.service;

import com.wavefront.sdk.common.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.zip.GZIPOutputStream;

/**
 * ReportingService that reports entities to Proxy or Wavefront services.
 *
 * @author Mike McMahon (mike.mcmahon@wavefront.com)
 */
public class ReportingService implements ReportAPI {

  private final String token;
  private final URI uri;

  private static final int CONNECT_TIMEOUT_MILLIS = 30000;
  private static final int READ_TIMEOUT_MILLIS = 10000;
  private static final int BUFFER_SIZE = 4096;

  public ReportingService(String server, @Nullable String token) {
    this.uri = URI.create(server);
    this.token = token;
  }

  @Override
  public int send(String format, InputStream stream) throws IOException {
    HttpURLConnection urlConn = null;
    int statusCode = 400;
    try {
      String originalPath = uri.getPath() != null ? uri.getPath() : "";
      URL url = new URL(uri.getScheme(), uri.getHost(), uri.getPort(), originalPath + "?/f=" + format);
      urlConn = (HttpURLConnection) url.openConnection();
      urlConn.setDoOutput(true);
      urlConn.addRequestProperty("Content-Type", "application/octet-stream");
      urlConn.addRequestProperty("Content-Encoding", "gzip");
      if (token != null && !token.equals("")) {
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
        byte[] buffer = new byte[BUFFER_SIZE];
        // read entire stream before closing
        while (is.read(buffer) > 0) {}
      }
    }
  }
}
