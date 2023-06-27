package com.wavefront.sdk.common.clients.service;

import java.io.IOException;
import java.io.InputStream;

/**
 * The API for reporting points to Proxy or Direct Data Ingestion
 *
 * @author Mike McMahon (mike.mcmahon@wavefront.com)
 * @version $Id: $Id
 */
public interface ReportAPI {
  /**
   * <p>send.</p>
   *
   * @param format a {@link java.lang.String} object
   * @param stream a {@link java.io.InputStream} object
   * @return a int
   */
  int send(String format, InputStream stream);

  /**
   * <p>sendEvent.</p>
   *
   * @param stream a {@link java.io.InputStream} object
   * @return a int
   */
  int sendEvent(InputStream stream);
}
