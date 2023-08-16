package com.wavefront.sdk.common.clients.service.token;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CSPAuthorizeResponse {
  @JsonProperty("id_token")
  public String idToken;

  @JsonProperty("token_type")
  public String tokenType;

  // This is in seconds
  @JsonProperty("expires_in")
  public int expiresIn;

  public String scope;

  @JsonProperty("access_token")
  public String accessToken;

  @JsonProperty("refresh_token")
  public String refreshToken;
}
