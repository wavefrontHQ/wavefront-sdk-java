package com.wavefront.sdk.entities.events;

import com.wavefront.sdk.common.annotation.Nullable;

import java.io.IOException;
import java.util.Map;

/**
 * WavefrontEventSender interface that sends an event to Wavefront
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 * @version $Id: $Id
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
   * @param annotations    The annotations (details, type, severity, e.g.) associated with this event
   * @throws java.io.IOException   if there was an error sending the event.
   */
  void sendEvent(String name, long startMillis, long endMillis, @Nullable String source,
                 @Nullable Map<String, String> tags,
                 @Nullable Map<String, String> annotations)
      throws IOException;
}
