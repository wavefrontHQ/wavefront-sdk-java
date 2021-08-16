package com.wavefront.sdk.common.clients;

import com.wavefront.sdk.common.Pair;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WavefrontClientFactoryTest {
  @Nested
  class ParseEndpoint {
    @Test
    void throwsForNonHTTPScheme() {
      assertThrows(IllegalArgumentException.class,
          () -> WavefrontClientFactory.parseEndpoint("udp://host:2878"));
    }

    @ParameterizedTest
    @CsvSource({
        "https://host     , https://host",
        "https://host:4443, https://host:4443",
        "http://host      , proxy://host",
        "http://host:2878 , proxy://host:2878",
        "http://host      , http://host",
        "http://host:2878 , http://host:2878",
    })
    void simpleCases(String expectedUrl, String input) {
      Pair<String, String> parsed = WavefrontClientFactory.parseEndpoint(input);

      assertEquals(expectedUrl, parsed._1);
      assertNull(parsed._2);
    }

    @ParameterizedTest
    @CsvSource({
        "https://host     , https://usr@host",
        "https://host:4443, https://usr@host:4443",
        "https://host:4443, https://usr@host:4443/path/",
    })
    void setsTokenFromUserInfo(String expectedUrl, String input) {
      Pair<String, String> parsed = WavefrontClientFactory.parseEndpoint(input);

      assertEquals(expectedUrl, parsed._1);
      assertEquals("usr", parsed._2);
    }

    @ParameterizedTest
    @CsvSource({
        "http://host     , proxy://usr@host",
        "http://host:2878, proxy://usr@host:2878",
        "http://host:2878, http://usr@host:2878/path/",
    })
    void dropsTokenForUnencryptedEndpoint(String expectedUrl, String input) {
      Pair<String, String> parsed = WavefrontClientFactory.parseEndpoint(input);

      assertEquals(expectedUrl, parsed._1);
      assertNull(parsed._2);
    }

    @ParameterizedTest
    @CsvSource({
        "https://host    , https://usr@host/path/",
        "http://host     , proxy://host/path/",
        "http://host:2878, http://host:2878/path/",
    })
    void stripsPathFromEndpoint(String expectedUrl, String input) {
      Pair<String, String> parsed = WavefrontClientFactory.parseEndpoint(input);

      assertEquals(expectedUrl, parsed._1);
    }
  }
}
