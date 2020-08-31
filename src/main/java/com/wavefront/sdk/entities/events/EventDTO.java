package com.wavefront.sdk.entities.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wavefront.sdk.common.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * DTO for the event to be sent to Wavefront.
 *
 * @author Shipeng Xie (xshipeng@vmware.com)
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventDTO {
  private final String name;
  private final long startTime;
  private final long endTime;
  private final List<String> hosts;
  private final List<String> tags;
  private final Map<String, String> annotations;

  public EventDTO(String name, long startTime, long endTime, String source,
                  Map<String, String> annotations, @Nullable List<String> listTags) {
    this.name = name;
    this.startTime = startTime;
    this.endTime = endTime;
    this.hosts = Collections.singletonList(source);
    this.tags = listTags;
    this.annotations = annotations;
  }

  public String getName() {
    return name;
  }

  public long getStartTime() {
    return startTime;
  }

  public long getEndTime() {
    return endTime;
  }

  public List<String> getHosts() {
    return hosts;
  }

  public List<String> getTags() {
    if (tags == null) {
      return null;
    }
    return Collections.unmodifiableList(tags);
  }

  public Map<String, String> getAnnotations() {
    return Collections.unmodifiableMap(annotations);
  }

}
