package com.wavefront.sdk.direct.ingestion;

import com.wavefront.sdk.common.clients.WavefrontClientFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * The API for reporting points directly to a Wavefront server.
 *
 * This class will be removed in future versions in favor of
 *  {@link WavefrontClientFactory} to construct Proxy and DirectDataIngestion senders.
 *
 * @author Vikram Raman
 */
@Deprecated
public interface DataIngesterAPI {
  /**
   * Returns the HTTP response's status code.
   */
  int report(String format, InputStream stream) throws IOException;
}
