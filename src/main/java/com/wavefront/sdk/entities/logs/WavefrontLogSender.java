package com.wavefront.sdk.entities.logs;

import com.wavefront.sdk.common.annotation.Nullable;

import java.io.IOException;
import java.util.Map;

/**
 * <p>WavefrontLogSender interface.</p>
 *
 * @author goppegard
 * @version $Id: $Id
 */
public interface WavefrontLogSender {
  /**
   * This method is used for sending log meta-data to wavefront. This metadata will be stored in LOG atom in wavefront.
   *
   * @param name      Log file name/source
   * @param value     Value for log stream
   * @param timestamp epoch timestamp
   * @param source    Logging source
   * @param tags      Labels associated with log source
   * @throws java.io.IOException throws an Exception
   */
  void sendLog(String name, double value, @Nullable Long timestamp, @Nullable String source,
               @Nullable Map<String, String> tags) throws IOException;
}
