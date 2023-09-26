package com.wavefront.sdk.common.clients.service.token;

// Primarily for proxy usage
public class NoopProxyTokenService implements TokenService {

  public NoopProxyTokenService() {
  }

  @Override
  public String getToken() {
    return "";
  }

  @Override
  public String getType() {
    return "No-Op/Proxy";
  }
}
