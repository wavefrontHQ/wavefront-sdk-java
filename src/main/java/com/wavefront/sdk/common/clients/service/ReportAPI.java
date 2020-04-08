package com.wavefront.sdk.common.clients.service;

import java.io.IOException;
import java.io.InputStream;

/**
 * The API for reporting points to Proxy or Direct Data Ingestion
 *
 * @author Mike McMahon (mike.mcmahon@wavefront.com)
 */
public interface ReportAPI {
  int send(String format, InputStream stream) throws IOException;
}
