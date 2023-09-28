package com.wavefront.sdk.common.clients.service.token;

public class WavefrontTokenService implements TokenService {
  private final String token;
  private final Type type = Type.WAVEFRONT_API_TOKEN;

  public WavefrontTokenService(final String token) {
    this.token = token;
  }

  @Override
  public String getToken() {
    return token;
  }

  @Override
  public Type getType() {  return type;  }
}
