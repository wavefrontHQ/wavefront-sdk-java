package com.wavefront.sdk.common;

import java.io.IOException;

/**
 * Buffer flusher that is responsible for flushing the buffer whenever flush method is invoked.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public interface BufferFlusher {

  /**
   * Flushes buffer, if applicable
   *
   * @throws IOException
   */
  void flush() throws IOException;

  /**
   * Returns the number of failed writes to the server.
   *
   * @return the number of failed writes to the server
   */
  int getFailureCount();
}
