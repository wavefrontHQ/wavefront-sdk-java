package com.wavefront.sdk.common.clients.service.token;

// Primarily for proxy usage
public class NoopTokenService implements TokenService {

  public NoopTokenService() {
  }

  @Override
  public String getToken() {
    return "";
  }

  @Override
  public String getType() {
    return "NOOP Token";
  }
}
