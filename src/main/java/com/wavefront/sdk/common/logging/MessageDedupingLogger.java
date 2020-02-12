package com.wavefront.sdk.common.logging;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A logger that suppresses identical messages for a specified period of time.
 *
 * @author Han Zhang (zhanghan@vmware.com)
 */
public class MessageDedupingLogger extends DelegatingLogger {
  private final long millisBetweenMessages;
  private final Supplier<Long> clockMillis;
  private final ConcurrentHashMap<String, AtomicLong> cache;

  /**
   * @param delegate              Delegate logger.
   * @param millisBetweenMessages Minimum period in millis between identical messages.
   */
  public MessageDedupingLogger(Logger delegate, long millisBetweenMessages) {
    this(delegate, millisBetweenMessages, System::currentTimeMillis);
  }

  /**
   * @param delegate              Delegate logger.
   * @param millisBetweenMessages Minimum period in millis between identical messages.
   * @param clockMillis           Supplier of current time in millis.
   */
  public MessageDedupingLogger(Logger delegate, long millisBetweenMessages,
                               Supplier<Long> clockMillis) {
    super(delegate);
    this.millisBetweenMessages = millisBetweenMessages;
    this.clockMillis = clockMillis;
    this.cache = new ConcurrentHashMap<>();
  }

  @Override
  public void log(Level level, String message) {
    if (tryAcquire(message)) {
      log(new LogRecord(level, message));
    }
  }

  /**
   * Log a message, de-duplicating with an alternate key.
   *
   * @param level               Log level.
   * @param message             String to write to log.
   * @param messageDedupingKey  String to dedupe the log by.
   */
  public void logWithAlternateKey(Level level, String message, String messageDedupingKey) {
    if (tryAcquire(messageDedupingKey)) {
      log(new LogRecord(level, message));
    }
  }

  private boolean tryAcquire(String key) {
    AtomicLong permitted = cache.computeIfAbsent(key, k -> new AtomicLong(0));
    long currentTimeMillis = clockMillis.get();
    long permittedTimeMillis = permitted.getAndUpdate(t -> t <= currentTimeMillis ?
        currentTimeMillis + millisBetweenMessages : t);
    return permittedTimeMillis <= currentTimeMillis;
  }
}