package com.wavefront.sdk.common.clients.service.token;

import com.wavefront.sdk.common.Utils;
import com.wavefront.sdk.common.annotation.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;

public abstract class CSPURLConnectionFactory {
  protected String cspBaseURL = "https://console.cloud.vmware.com";
  protected int connectTimeoutMillis = 30_000;
  protected int readTimeoutMillis = 10_000;

  public CSPURLConnectionFactory(@Nullable String cspBaseURL) {
    if (!Utils.isNullOrEmpty(cspBaseURL)) {
      this.cspBaseURL = cspBaseURL;
    }
  }

  public abstract HttpURLConnection build() throws IOException;

  public abstract byte[] getPostData();

  public abstract TokenService.Type getTokenType();
}
