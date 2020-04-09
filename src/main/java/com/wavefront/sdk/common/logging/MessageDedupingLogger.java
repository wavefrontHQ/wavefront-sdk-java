package com.wavefront.sdk.common.logging;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A logger that suppresses identical messages for a specified period of time.
 *
 * @author Han Zhang (zhanghan@vmware.com)
 */
@SuppressWarnings("UnstableApiUsage")
public class MessageDedupingLogger extends DelegatingLogger {
  private final LoadingCache<String, RateLimiter> rateLimiterCache;

  /**
   * @param delegate     Delegate logger.
   * @param maximumSize  max number of unique messages that can exist in the cache
   * @param rateLimit    rate limit (per second per each unique message)
   */
  public MessageDedupingLogger(Logger delegate, long maximumSize, double rateLimit) {
    super(delegate);
    this.rateLimiterCache = CacheBuilder.newBuilder().
        expireAfterAccess((long)(2 / rateLimit), TimeUnit.SECONDS).
        maximumSize(maximumSize).
        build(new CacheLoader<String, RateLimiter>() {
          @Override
          public RateLimiter load(String s) {
            return RateLimiter.create(rateLimit);
          }
        });
  }

  @Override
  public void log(Level level, String message) {
    try {
      if (Objects.requireNonNull(rateLimiterCache.get(message)).tryAcquire()) {
        log(new LogRecord(level, message));
      }
    } catch (ExecutionException e) {
      // Log the message if we encounter an error fetching the rate limiter
      log(new LogRecord(level, message));
    }
  }

  /**
   * Log a message, de-duplicating with the specified key.
   *
   * @param messageDedupingKey  String to dedupe the log by.
   * @param level               Log level.
   * @param message             String to write to log.
   */
  public void log(String messageDedupingKey, Level level, String message) {
    try {
      if (Objects.requireNonNull(rateLimiterCache.get(messageDedupingKey)).tryAcquire()) {
        log(new LogRecord(level, message));
      }
    } catch (ExecutionException e) {
      // Log the message if we encounter an error fetching the rate limiter
      log(new LogRecord(level, message));
    }
  }
}
