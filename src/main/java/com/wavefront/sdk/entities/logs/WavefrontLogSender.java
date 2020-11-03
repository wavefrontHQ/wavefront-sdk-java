package com.wavefront.sdk.entities.logs;

import com.wavefront.sdk.common.annotation.Nullable;

import java.io.IOException;
import java.util.Map;

public interface WavefrontLogSender {
  void sendLog(String name, double value, @Nullable Long timestamp, @Nullable String source,
               @Nullable Map<String, String> tags) throws IOException;
}
