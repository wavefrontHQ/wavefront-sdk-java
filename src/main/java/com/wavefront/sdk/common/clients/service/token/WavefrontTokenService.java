package com.wavefront.sdk.common.clients.service.token;

public class WavefrontTokenService implements TokenService {
  private final String token;

  public WavefrontTokenService(final String token) {
    this.token = token;
  }

  @Override
  public String getToken() {
    return token;
  }
}
