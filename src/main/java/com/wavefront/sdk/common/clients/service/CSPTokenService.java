package com.wavefront.sdk.common.clients.service;

public class CSPTokenService implements TokenService {

  private final String cspBaseURL;
  private final String cspClientId;
  private final String cspClientSecret;

  public CSPTokenService(final String cspBaseURL, final String cspClientId, final String cspClientSecret) {
    this.cspBaseURL = cspBaseURL;
    this.cspClientId = cspClientId;
    this.cspClientSecret = cspClientSecret;
  }

  // TODO - put in CSP client

  @Override
  public String getToken() {
    return "";
  }
}
