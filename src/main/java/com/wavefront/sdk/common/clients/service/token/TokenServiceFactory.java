package com.wavefront.sdk.common.clients.service.token;

import com.wavefront.sdk.common.annotation.Nullable;

public class TokenServiceFactory {
  public static TokenService create(TokenService.Type type, String token, @Nullable String cspBaseURL) {
    switch (type) {
      case CSP_API_TOKEN:
        return new CSPTokenService(new CSPUserTokenURLConnectionFactory(cspBaseURL, token));
      case CSP_CLIENT_CREDENTIALS:
        return new CSPTokenService(new CSPServerTokenURLConnectionFactory(token));
      case WAVEFRONT_API_TOKEN:
        return new WavefrontTokenService(token);
      case NO_TOKEN:
        return new NoopProxyTokenService();
      default:
        return new NoopProxyTokenService();
    }
  }

}
