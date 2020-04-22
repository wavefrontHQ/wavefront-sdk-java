package com.wavefront.sdk.common.application;

import com.google.common.base.Strings;
import com.wavefront.sdk.common.Constants;
import com.wavefront.sdk.common.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wavefront.sdk.common.Constants.APPLICATION_TAG_KEY;
import static com.wavefront.sdk.common.Constants.CLUSTER_TAG_KEY;
import static com.wavefront.sdk.common.Constants.SERVICE_TAG_KEY;
import static com.wavefront.sdk.common.Constants.SHARD_TAG_KEY;

/**
 * Metadata about your application represented as tags in Wavefront.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class ApplicationTags {
  private final String application;
  @Nullable
  private final String cluster;
  private final String service;
  @Nullable
  private final String shard;
  private final Map<String, String> customTags;

  public static class Builder {
    // Required parameters
    private final String application;
    private final String service;

    // Optional parameters
    @Nullable
    private String cluster;
    @Nullable
    private String shard;
    private Map<String, String> customTags = new HashMap<>();

    /**
     * Builder to build ApplicationTags.
     *
     * @param application Name of the application.
     * @param service     Name of the service.
     */
    public Builder(String application, String service) {
      this.application = application;
      this.service = service;
    }

    /**
     * Set the cluster (example: us-west-1/us-west-2 etc.) in which your application is running.
     * This setting is optional.
     *
     * @param cluster cluster in which your application is running.
     * @return {@code this}.
     */
    public Builder cluster(String cluster) {
      this.cluster = cluster;
      return this;
    }

    /**
     * Set the shard (example: primary/secondary etc.) in which your application is running.
     * This setting is optional.
     *
     * @param shard shard where your application is running.
     * @return {@code this}.
     */
    public Builder shard(String shard) {
      this.shard = shard;
      return this;
    }

    /**
     * Set additional custom tags for your application.
     * For instance: {location: SF}, {env: Staging} etc.
     * This setting is optional.
     *
     * @param customTags Additional custom tags/metadata for your application.
     * @return {@code this}
     */
    public Builder customTags(Map<String, String> customTags) {
      this.customTags.putAll(customTags);
      return this;
    }

    /**
     * Set additional custom tags from environment variables that matches the a
     * regular expressions.
     * This setting is optional.
     *
     * @param regex Regular expression.
     * @return {@code this}
     */
    public Builder tagsFromEnv(String regex) {
      Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
      Map<String, String> env = System.getenv();
      env.forEach((var, value) -> {
        Matcher matcher = pattern.matcher(var);
        if (matcher.matches()) {
            if (!Strings.isNullOrEmpty(value)) {
              this.customTags.put(var, value);
            }
        }
      });
      return this;
    }
    
    /**
     * Set additional a custom tag from environment variable.
     * This setting is optional.
     *
     * @param varName Regular environment variable name.
     * @param tagName Custom tag name.
     * @return {@code this}
     */
   public Builder tagFromEnv(String varName, String tagName) {
      String value = System.getenv().get(varName);
      if (!Strings.isNullOrEmpty(value)) {
          this.customTags.put(tagName, value);
      }
      return this;
    }

    /**
     * Build application tags.
     *
     * @return {@link ApplicationTags}.
     */
    public ApplicationTags build() {
      return new ApplicationTags(this);
    }
  }

  private ApplicationTags(Builder builder) {
    application = builder.application;
    cluster = builder.cluster;
    service = builder.service;
    shard = builder.shard;
    customTags = builder.customTags;
  }

  /**
   * Fetch the application name.
   *
   * @return name of the application.
   */
  public String getApplication() {
    return application;
  }

  /**
   * Fetch the cluster name.
   *
   * @return name of the cluster.
   */
  @Nullable
  public String getCluster() {
    return cluster;
  }

  /**
   * Fetch the service name
   *
   * @return name of the service
   */
  public String getService() {
    return service;
  }

  /**
   * Fetch the shard name.
   *
   * @return name of the shard.
   */
  @Nullable
  public String getShard() {
    return shard;
  }

  /**
   * Fetch the custom tags.
   *
   * @return custom tags.
   */
  @Nullable
  public Map<String, String> getCustomTags() {
    return customTags;
  }

  /**
   * Converts ApplicationTags to PointTags HashMap.
   *
   * @return PointTag Map which is immutable.
   */
  public Map<String, String> toPointTags() {
    return Collections.unmodifiableMap(new HashMap<String, String>() {{
      put(APPLICATION_TAG_KEY, application);
      put(CLUSTER_TAG_KEY, cluster == null ? Constants.NULL_TAG_VAL : cluster);
      put(SERVICE_TAG_KEY, service);
      put(SHARD_TAG_KEY, shard == null ? Constants.NULL_TAG_VAL : shard);
      if (customTags != null) {
        putAll(customTags);
      }
    }});
  }
}
