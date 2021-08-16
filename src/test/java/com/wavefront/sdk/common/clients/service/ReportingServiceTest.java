package com.wavefront.sdk.common.clients.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportingServiceTest {
  @ParameterizedTest
  @CsvSource({
      "http://127.0.0.1:2878/report?f=wavefront         , http://127.0.0.1:2878",
      "http://127.0.0.1:2878/report?f=wavefront         , http://127.0.0.1:2878/",
      "http://127.0.0.1:2878/report?f=wavefront         , http://127.0.0.1:2878////",
      "http://localhost:2878/report?f=wavefront         , http://localhost:2878/report",
      "http://localhost:2878/report?f=wavefront         , http://localhost:2878/report/",
      "https://domain.wavefront.com/report?f=wavefront  , https://domain.wavefront.com",
      "https://domain.wavefront.com/report?f=wavefront  , https://domain.wavefront.com/",
      "https://domain.wavefront.com/report?f=wavefront  , https://domain.wavefront.com/report",
      "https://domain.wavefront.com/report?f=wavefront  , https://domain.wavefront.com/report/",
      "http://a.proxy.b.com:2878/prod/report?f=wavefront, http://a.proxy.b.com:2878/prod/report/",
  })
  void testGetReportingUrl(String expectedUrl, String input) {
    URL actualUrl = assertDoesNotThrow(() ->
        ReportingService.getReportingUrl(URI.create(input), "wavefront"));

    assertEquals(expectedUrl, actualUrl.toString());
  }

  @ParameterizedTest
  @CsvSource({
      "http://127.0.0.1:2878/api/v2/event         , http://127.0.0.1:2878",
      "http://127.0.0.1:2878/api/v2/event         , http://127.0.0.1:2878/",
      "http://127.0.0.1:2878/api/v2/event         , http://127.0.0.1:2878////",
      "http://localhost:2878/api/v2/event         , http://localhost:2878/api/v2/event",
      "http://localhost:2878/api/v2/event         , http://localhost:2878/api/v2/event/",
      "https://domain.wavefront.com/api/v2/event  , https://domain.wavefront.com",
      "https://domain.wavefront.com/api/v2/event  , https://domain.wavefront.com/",
      "https://domain.wavefront.com/api/v2/event  , https://domain.wavefront.com/api/v2/event",
      "https://domain.wavefront.com/api/v2/event  , https://domain.wavefront.com/api/v2/event/",
      "http://a.proxy.b.com:2878/prod/api/v2/event, http://a.proxy.b.com:2878/prod/api/v2/event/",
  })
  void testGetEventReportingUrl(String expectedUrl, String input) {
    URL actualUrl = assertDoesNotThrow(() ->
        ReportingService.getEventReportingUrl(URI.create(input)));

    assertEquals(expectedUrl, actualUrl.toString());
  }
}
