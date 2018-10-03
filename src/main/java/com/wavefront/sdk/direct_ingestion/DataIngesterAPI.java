package com.wavefront.sdk.direct_ingestion;

import com.wavefront.sdk.common.Pair;

import java.io.IOException;
import java.io.InputStream;

/**
 * The API for reporting points directly to a Wavefront server.
 *
 * @author Vikram Raman
 */
public interface DataIngesterAPI {
  /**
   * Returns a {@link Pair} consisting of the HTTP response's status code and response message.
   */
  Pair<Integer, String> report(String format, InputStream stream) throws IOException;
}
