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
 * @version $Id: $Id
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventDTO {
  private final String name;
  private final long startTime;
  private final long endTime;
  private final List<String> hosts;
  private final List<String> tags;
  private final Map<String, String> annotations;

  /**
   * <p>Constructor for EventDTO.</p>
   *
   * @param name a {@link java.lang.String} object
   * @param startTime a long
   * @param endTime a long
   * @param source a {@link java.lang.String} object
   * @param annotations a {@link java.util.Map} object
   * @param listTags a {@link java.util.List} object
   */
  public EventDTO(String name, long startTime, long endTime, String source,
                  Map<String, String> annotations, @Nullable List<String> listTags) {
    this.name = name;
    this.startTime = startTime;
    this.endTime = endTime;
    this.hosts = Collections.singletonList(source);
    this.tags = listTags;
    this.annotations = annotations;
  }

  /**
   * <p>Getter for the field <code>name</code>.</p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getName() {
    return name;
  }

  /**
   * <p>Getter for the field <code>startTime</code>.</p>
   *
   * @return a long
   */
  public long getStartTime() {
    return startTime;
  }

  /**
   * <p>Getter for the field <code>endTime</code>.</p>
   *
   * @return a long
   */
  public long getEndTime() {
    return endTime;
  }

  /**
   * <p>Getter for the field <code>hosts</code>.</p>
   *
   * @return a {@link java.util.List} object
   */
  public List<String> getHosts() {
    return hosts;
  }

  /**
   * <p>Getter for the field <code>tags</code>.</p>
   *
   * @return a {@link java.util.List} object
   */
  public List<String> getTags() {
    if (tags == null) {
      return null;
    }
    return Collections.unmodifiableList(tags);
  }

  /**
   * <p>Getter for the field <code>annotations</code>.</p>
   *
   * @return a {@link java.util.Map} object
   */
  public Map<String, String> getAnnotations() {
    return Collections.unmodifiableMap(annotations);
  }

}
