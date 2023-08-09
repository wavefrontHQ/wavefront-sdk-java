package com.wavefront.sdk.common.clients.service;

import com.fasterxml.jackson.annotation.JsonProperty;
public class CSPResponse {
  @JsonProperty("id_token")
  public String idToken;

  @JsonProperty("token_type")
  public String tokenType;

  // This is a timestamp
  @JsonProperty("expires_in")
  public int expiresIn;

  public String scope;

  @JsonProperty("access_token")
  public String accessToken;

  @JsonProperty("refresh_token")
  public String refreshToken;
}
