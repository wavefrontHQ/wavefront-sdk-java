package com.wavefront.sdk.common.clients.service.token;

import java.io.IOException;
import java.net.HttpURLConnection;

public interface CSPURLConnectionFactory {
  HttpURLConnection build() throws IOException;

  byte[] getPostData();
}
