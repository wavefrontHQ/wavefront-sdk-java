package com.wavefront.sdk.common.clients.service.token;

import java.io.IOException;
import java.net.HttpURLConnection;

public interface CSPURLConnectionFactory {
  String DEFAULT_BASE_URL = "https://console.cloud.vmware.com/";

  HttpURLConnection build() throws IOException;

  byte[] getPostData();

  String getType();
}
