package com.wavefront.sdk.common.clients.service;

import static com.wavefront.sdk.common.clients.service.token.CSPTokenService.hasDirectIngestScope;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

public class CSPTokenServiceTest {
    @Test
    public void testHasDirectIngestScope() {
        final String uuid = UUID.randomUUID().toString();

        final String scopeString = "external/" + uuid + "/*/aoa:directDataIngestion external/" + uuid + "/aoa:directDataIngestion csp:org_member";

        assertTrue(hasDirectIngestScope(scopeString));
        assertFalse(hasDirectIngestScope("no direct data ingestion scope"));
        assertFalse(hasDirectIngestScope(""));
        assertFalse(hasDirectIngestScope(null));
        assertTrue(hasDirectIngestScope("aoa/*"));
        assertTrue(hasDirectIngestScope("some aoa/*"));
        assertTrue(hasDirectIngestScope("aoa:*"));
        assertTrue(hasDirectIngestScope("some aoa:*"));
    }
}
