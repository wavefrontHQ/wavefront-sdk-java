package com.wavefront.sdk.common.clients.service.token;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CSPServerTokenURLConnectionFactoryTest {

  @Test
  void parseClientCredentials() {
    Map<CSPServerTokenURLConnectionFactory.CredentialPart, String> subject;

    subject = CSPServerTokenURLConnectionFactory.parseClientCredentials("clientId=cid,clientSecret=csc");
    assertEquals("cid", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.CLIENT_ID));
    assertEquals("csc", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.CLIENT_SECRET));

    subject = CSPServerTokenURLConnectionFactory.parseClientCredentials("clientId=cid,clientSecret=csc,orgId=oid");
    assertEquals("cid", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.CLIENT_ID));
    assertEquals("csc", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.CLIENT_SECRET));
    assertEquals("oid", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.ORG_ID));

    subject = CSPServerTokenURLConnectionFactory.parseClientCredentials("clientId=cid,clientSecret=csc,baseUrl=https://csp-dev.vmware.com");
    assertEquals("cid", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.CLIENT_ID));
    assertEquals("csc", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.CLIENT_SECRET));
    assertEquals("https://csp-dev.vmware.com", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.BASE_URL));

    subject = CSPServerTokenURLConnectionFactory.parseClientCredentials("clientId=cid,clientSecret=csc,orgId=oid,baseUrl=https://csp-dev.vmware.com");
    assertEquals("cid", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.CLIENT_ID));
    assertEquals("csc", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.CLIENT_SECRET));
    assertEquals("oid", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.ORG_ID));
    assertEquals("https://csp-dev.vmware.com", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.BASE_URL));
  }

  @Test
  void parseClientCredentialsWithOddInput() {
    Map<CSPServerTokenURLConnectionFactory.CredentialPart, String> subject;

    // quotes
    subject = CSPServerTokenURLConnectionFactory.parseClientCredentials("clientId=\"cid\",clientSecret='csc'");
    assertEquals("cid", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.CLIENT_ID));
    assertEquals("csc", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.CLIENT_SECRET));

    // whitespace
    subject = CSPServerTokenURLConnectionFactory.parseClientCredentials("clientId=cid, clientSecret=csc");
    assertEquals("cid", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.CLIENT_ID));
    assertEquals("csc", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.CLIENT_SECRET));

    // out of order
    subject = CSPServerTokenURLConnectionFactory.parseClientCredentials("orgId=oid,clientSecret=csc,clientId=cid");
    assertEquals("cid", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.CLIENT_ID));
    assertEquals("csc", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.CLIENT_SECRET));
    assertEquals("oid", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.ORG_ID));

    // case-insensitive
    subject = CSPServerTokenURLConnectionFactory.parseClientCredentials("clientid=cid,clientSecret=csc");
    assertEquals("cid", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.CLIENT_ID));
    assertEquals("csc", subject.get(CSPServerTokenURLConnectionFactory.CredentialPart.CLIENT_SECRET));
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