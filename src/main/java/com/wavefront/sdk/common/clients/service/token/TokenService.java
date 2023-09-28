package com.wavefront.sdk.common.clients.service.token;

public interface TokenService {
  String getToken();
  Type getType();

  enum Type {
    NO_TOKEN("No-Op/Proxy"),
    WAVEFRONT_API_TOKEN("Wavefront API Token"),
    CSP_API_TOKEN("CSP API Token"),
    CSP_CLIENT_CREDENTIALS("CSP Client Credentials");

    private final String type;

    Type(String type) {
      this.type = type;
    }

    @Override
    public String toString() {
      return type;
    }
  }
}
