package com.wavefront.sdk.common.clients.service.token;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CSPServerTokenURLConnectionFactoryTest {

  @Test
  void parseClientCredentials() {
    Map<String, String> creds;

    creds = CSPServerTokenURLConnectionFactory.parseClientCredentials("clientId=cid,clientSecret=csc");
    assertEquals("cid", creds.get("clientId"));
    assertEquals("csc", creds.get("clientSecret"));

    creds = CSPServerTokenURLConnectionFactory.parseClientCredentials("clientId=cid,clientSecret=csc,orgId=oid");
    assertEquals("cid", creds.get("clientId"));
    assertEquals("csc", creds.get("clientSecret"));
    assertEquals("oid", creds.get("orgId"));
  }

  @Test
  void parseClientCredentialsWithOddInput() {
    Map<String, String> creds;

    // whitespace
    creds = CSPServerTokenURLConnectionFactory.parseClientCredentials("clientId=cid, clientSecret=csc");
    assertEquals("cid", creds.get("clientId"));
    assertEquals("csc", creds.get("clientSecret"));

    // out of order
    creds = CSPServerTokenURLConnectionFactory.parseClientCredentials("orgId=oid,clientSecret=csc,clientId=cid");
    assertEquals("cid", creds.get("clientId"));
    assertEquals("csc", creds.get("clientSecret"));
    assertEquals("oid", creds.get("orgId"));

    // case-insensitive
    creds = CSPServerTokenURLConnectionFactory.parseClientCredentials("clientid=cid,clientSecret=csc");
    assertEquals("cid", creds.get("clientId"));
    assertEquals("csc", creds.get("clientSecret"));
  }

  @Test
  void parseClientCredentialsThrowsOnBadInput() {
    assertThrows(IllegalArgumentException.class,
        () -> CSPServerTokenURLConnectionFactory.parseClientCredentials("clientId=foo"));

    assertThrows(IllegalArgumentException.class,
        () -> CSPServerTokenURLConnectionFactory.parseClientCredentials("clientId=foo,clientSecret=Bar,orgId=org,extra=bad"));

    assertThrows(IllegalArgumentException.class,
        () -> CSPServerTokenURLConnectionFactory.parseClientCredentials("clientId=,clientSecret=Bar"));

    assertThrows(IllegalArgumentException.class,
        () -> CSPServerTokenURLConnectionFactory.parseClientCredentials("clientId=foo,clientSecret=Bar,orgId="));
  }
}