package com.wavefront.sdk.common.clients.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CSPTokenServiceTest {

  @Test
  public void testTheThing() {
    CSPTokenService cspTokenService = new CSPTokenService("https://console-stg.cloud.vmware.com", "REPLACE_ME", "REPLACE_ME");

    assertNotNull(cspTokenService.getToken());
  }
}