package com.wavefront.sdk.common.logging;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Base class for delegating loggers.
 *
 * @author vasily@wavefront.com
 * @version $Id: $Id
 */
public abstract class DelegatingLogger extends Logger {
  protected final Logger delegate;

  /**
   * <p>Constructor for DelegatingLogger.</p>
   *
   * @param delegate     Delegate logger.
   */
  public DelegatingLogger(Logger delegate) {
    super(delegate.getName(), null);
    this.delegate = delegate;
  }

  /** {@inheritDoc} */
  @Override
  public abstract void log(Level level, String message);

  /** {@inheritDoc} */
  @Override
  public void log(LogRecord logRecord) {
    logRecord.setLoggerName(delegate.getName());
    // Infer caller so that the log message contains the right '[source class] [source method]'
    // instead of 'DelegatingLogger log'
    inferCaller(logRecord);
    delegate.log(logRecord);
  }

  /**
   * This is a JDK8-specific implementation that is quite expensive because it fetches the
   * current stack trace. TODO: switch to StackWalker after migrating to JDK9+
   */
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
            !cname.startsWith("sun.reflect.")) {
          // We've found the relevant frame.
          logRecord.setSourceClassName(cname);
          logRecord.setSourceMethodName(frame.getMethodName());
          return;
        }
      }
    }
  }
}
