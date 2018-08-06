package com.wavefront.sdk.entities.events;

import java.io.IOException;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * WavefrontEventSender interface that sends an event to Wavefront
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public interface WavefrontEventSender {

  /**
   * Sends the given event to Wavefront
   *
   * @param name           The name of the event. Spaces are replaced with '-' (dashes) and
   *                       quotes will be automatically escaped.
   * @param startMillis    The timestamp in milliseconds when the event was started.
   * @param endMillis      The timestamp in milliseconds when the event was ended.
   * @param source         The source (or host) that's sending the event. If null, then assigned
   *                       by Wavefront.
   * @param tags           The tags associated with this event.
   * @throws IOException   if there was an error sending the event.
   */
  void sendEvent(String name, long startMillis, long endMillis, @Nullable String source,
                 @Nullable Map<String, String> tags) throws IOException;
}
