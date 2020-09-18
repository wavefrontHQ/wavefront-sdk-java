package com.wavefront.sdk.common.logging;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A logger that suppresses log messages for a specified period of time.
 *
 * @author Shipeng Xie (xshipeng@vmware.com).
 */
public class MessageSuppressingLogger extends DelegatingLogger {
  private final Cache<String, Long> cache;
  private final long suppressMillis;

  /**
   * @param delegate     Delegate logger.
   * @param suppressTime Time to suppress messages.
   * @param timeUnit     Time unit.
   */
  public MessageSuppressingLogger(Logger delegate, long suppressTime, TimeUnit timeUnit) {
    super(delegate);
    cache = CacheBuilder.newBuilder().maximumSize(1000).expireAfterAccess(2 * suppressTime, timeUnit).build();
    suppressMillis = timeUnit.toMillis(suppressTime);
  }

  /**
   * Suppress and log the message based on the specified key.
   *
   * @param messageKey String to suppress the log message.
   * @param level      Log level.
   * @param message    Log message.
   */
  public void log(String messageKey, Level level, String message) {
    cache.asMap().compute(messageKey, (key, prevTime) -> computeTimestamp(message, level, prevTime));
  }

  /**
   * Reset log suppressing for the specified key/log message.
   */
  public void reset(String key) {
    cache.invalidate(key);
  }

  @Override
  public void log(Level level, String message) {
    cache.asMap().compute(message, (key, prevTime) -> computeTimestamp(message, level, prevTime));
  }

  private Long computeTimestamp(String message, Level level, Long prevTime) {
    long currentTime = System.currentTimeMillis();
    if (prevTime == null) {
      return currentTime;
    }
    if (currentTime - prevTime > suppressMillis) {
      log(new LogRecord(level, message));
      return currentTime;
    }
    return prevTime;
  }
}
