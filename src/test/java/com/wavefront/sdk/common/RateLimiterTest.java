package com.wavefront.sdk.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RateLimiterTest {

    @Test
    public void testBasic() {
        Clock.currTime = System.nanoTime();
        RateLimiter rateLimiter = new RateLimiter(10.0, 0.0);
        assertTrue(rateLimiter.isPermitted(10.0));
        assertFalse(rateLimiter.isPermitted(1.0));

        // move ahead by 1 second
        Clock.currTime += 1e9;
        assertTrue(rateLimiter.isPermitted(10.0));
    }

    @Test
    public void testBurstiness() {
        Clock.currTime = System.nanoTime();
        RateLimiter rateLimiter = new RateLimiter(10.0, 0.2);
        assertTrue(rateLimiter.isPermitted(10.0));

        // move ahead by 50ms
        Clock.currTime += 50e6;
        assertFalse(rateLimiter.isPermitted(1.0));

        // move ahead by 5s. allowed should now be 12;
        Clock.currTime += 5e9;
        assertTrue(rateLimiter.isPermitted(12.0));
        assertFalse(rateLimiter.isPermitted(1.0));
    }
}
