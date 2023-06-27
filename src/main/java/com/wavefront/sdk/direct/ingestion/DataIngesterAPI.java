package com.wavefront.sdk.direct.ingestion;

import com.wavefront.sdk.common.clients.WavefrontClientFactory;

import java.io.InputStream;

/**
 * The API for reporting points directly to a Wavefront server.
 *
 * This class will be removed in future versions in favor of
 *  {@link com.wavefront.sdk.common.clients.WavefrontClientFactory} to construct Proxy and DirectDataIngestion senders.
 *
 * @author Vikram Raman
 * @version $Id: $Id
 */
@Deprecated
public interface DataIngesterAPI {
  /**
   * Returns the HTTP response's status code.
   *
   * @param format a {@link java.lang.String} object
   * @param stream a {@link java.io.InputStream} object
   * @return a int
   */
  int report(String format, InputStream stream);
}
