package com.wavefront.sdk.direct_ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.zip.GZIPOutputStream;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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
  public Response report(String format, InputStream stream) throws IOException {
    /*
     * Refer https://docs.oracle.com/javase/8/docs/technotes/guides/net/http-keepalive.html
     * for details around why this code is written as it is.
     */
    int statusCode = 400;
    String respMsg = BAD_REQUEST;
    HttpURLConnection urlConn = null;
    try {
      URL url = new URL(uri.getScheme(), uri.getHost(), uri.getPort(), "/report?f=" + format);
      urlConn = (HttpURLConnection) url.openConnection();
      urlConn.setDoOutput(true);
      urlConn.addRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM);
      urlConn.addRequestProperty(HttpHeaders.CONTENT_ENCODING, "gzip");
      urlConn.addRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer " + token);

      urlConn.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
      urlConn.setReadTimeout(READ_TIMEOUT_MILLIS);

      try (GZIPOutputStream gzipOS = new GZIPOutputStream(urlConn.getOutputStream())) {
        byte[] buffer = new byte[4096];
        while (stream.read(buffer) > 0) {
          gzipOS.write(buffer);
        }
        gzipOS.flush();
      }
      statusCode = urlConn.getResponseCode();
      respMsg = urlConn.getResponseMessage();
      readAndClose(urlConn.getInputStream());
    } catch (IOException ex) {
      if (urlConn != null) {
        statusCode = urlConn.getResponseCode();
        respMsg = urlConn.getResponseMessage();
        readAndClose(urlConn.getErrorStream());
      }
    }
    return Response.status(statusCode).entity(respMsg).build();
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
