package com.wavefront.sdk.direct.ingestion;

import java.io.IOException;
import java.io.InputStream;

/**
 * The API for reporting points directly to a Wavefront server.
 *
 * @author Vikram Raman
 */
public interface DataIngesterAPI {
  /**
   * Returns the HTTP response's status code.
   */
  int report(String format, InputStream stream) throws IOException;
}
