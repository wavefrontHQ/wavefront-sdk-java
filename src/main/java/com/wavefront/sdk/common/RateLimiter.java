package com.wavefront.sdk.common;

/**
 * A rate-limiter based on the token bucket algorithm.
 *
 * @author Vikram Raman
 */
public class RateLimiter {

    /** The rate of permits allowed per second. */
    private final double permitsPerSecond;

    /** The balance of permits remaining. */
    private double balance;

    /** The maximum permits to allow accounting for under utilization. */
    private double maxBalance;

    /** When the last permit was issued in nanoticks. */
    private long lastNanos;

    /**
     * Constructor.
     *
     * @param permitsPerSecond the number of permits per second
     * @param burstFactor the burst factor to account for under utilization and burstiness
     */
    public RateLimiter(double permitsPerSecond, double burstFactor) {
        this.permitsPerSecond = permitsPerSecond;
        this.balance = permitsPerSecond;
        this.maxBalance = permitsPerSecond + (permitsPerSecond * burstFactor);
        this.lastNanos = Clock.nanoTime();
    }

    public boolean isPermitted(double permits) {
        synchronized (this) {
            refill();
            if (balance < permits) {
                return false;
            }
            balance -= permits;
        }
        return true;
    }

    private void refill() {
        long now = Clock.nanoTime();
        double elapsed = (now - lastNanos) / 1.0e9; // elapsed in seconds
        lastNanos = now;

        // We track unused time and use it to acquire permits up to maxBalance
        // Rate is "permits / time". (permits / time) * time gives us the cost in terms of permits.
        double permits = elapsed * permitsPerSecond;
        balance = Math.min(maxBalance, balance + permits);
    }
}
