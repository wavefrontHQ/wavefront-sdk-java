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
 * @version $Id: $Id
 */
public class MessageSuppressingLogger extends DelegatingLogger {
  private final Cache<String, Long> cache;
  private final long suppressMillis;

  /**
   * <p>Constructor for MessageSuppressingLogger.</p>
   *
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
    if (!isLoggable(level)) {
      return;
    }

    cache.asMap().compute(messageKey,
        (key, prevTime) -> logMessageAndComputeTimestamp(message, level, prevTime));
  }

  /**
   * Reset log suppressing for the specified key/log message.
   *
   * @param key a {@link java.lang.String} object
   */
  public void reset(String key) {
    cache.invalidate(key);
  }

  /** {@inheritDoc} */
  @Override
  public void log(Level level, String message) {
    if (!isLoggable(level)) {
      return;
    }

    cache.asMap().compute(message,
        (key, prevTime) -> logMessageAndComputeTimestamp(message, level, prevTime));
  }

  /**
   * This is a JDK8-specific implementation that is quite expensive because it fetches the
   * current stack trace. TODO: switch to StackWalker after migrating to JDK9+
   */
  @Override
  void inferCaller(LogRecord logRecord) {
    StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
    boolean lookingForLogger = true;
    for (StackTraceElement frame : stackTraceElements) {
      String cname = frame.getClassName();
      if (lookingForLogger) {
        // Skip all frames until we have found the first logger frame.
        if (cname.endsWith("Logger")) {
          lookingForLogger = false;
        }
      } else {
        if (!cname.endsWith("Logger") && !cname.startsWith("java.lang.reflect.") &&
            !cname.startsWith("sun.reflect.") &&
            !cname.startsWith("com.google.common.cache.LocalCache")) {
          // We've found the relevant frame.
          logRecord.setSourceClassName(cname);
          logRecord.setSourceMethodName(frame.getMethodName());
          return;
        }
      }
    }
  }

  /**
   * Log a message based on the previous timestamp for the message key and set a new timestamp.
   *
   * @param message  String to write to log.
   * @param level    Log level.
   * @param prevTime Previous timestamp for the message key.
   * @return a new timestamp for the message key.
   */
  private Long logMessageAndComputeTimestamp(String message, Level level, Long prevTime) {
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
