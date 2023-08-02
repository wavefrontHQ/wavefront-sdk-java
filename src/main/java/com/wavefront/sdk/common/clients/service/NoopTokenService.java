package com.wavefront.sdk.common.clients.service;

// Primarily for proxy usage
public class NoopTokenService implements TokenService {

  public NoopTokenService() {
  }

  @Override
  public String getToken() {
    return "";
  }
}
