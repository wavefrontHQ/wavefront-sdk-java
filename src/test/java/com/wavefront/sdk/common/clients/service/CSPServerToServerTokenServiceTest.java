package com.wavefront.sdk.common.clients.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CSPTokenServiceTest {

  @Test
  public void testTheThing() throws InterruptedException {
    CSPTokenService cspTokenService = new CSPTokenService("https://console-stg.cloud.vmware.com", "cjqbsvWmbEkyNUJxqJy2jA7qEgOBFzhJdbI", "Fy1lDoRCE3ycvkFesHfzznHFjtsGKk74DEWHzfml1sDW5AT5vx");

    assertNotNull(cspTokenService.getToken());
  }
}