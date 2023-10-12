package com.wavefront.sdk.common.logging;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DelegatingLogger}.
 *
 * @author Glenn Oppegard (goppegard@vmware.com).
 */
class DelegatingLoggerTest {

  @Test
  void skipsDelegatingBasedOnLevel() {
    Logger mockLogger = EasyMock.createMock(Logger.class);
    reset(mockLogger);
    expect(mockLogger.getName()).andReturn("loggerName").anyTimes();
    replay(mockLogger);
    DelegatingLoggerImpl log = new DelegatingLoggerImpl(mockLogger);

    reset(mockLogger);
    expect(mockLogger.getName()).andReturn("loggerName").anyTimes();
    Capture<LogRecord> logs = Capture.newInstance(CaptureType.ALL);
    mockLogger.log(capture(logs));
    expectLastCall().times(5);
    replay(mockLogger);

    log.setLevel(Level.FINE);
    log.severe("severe");
    log.warning("warning");
    log.info("info");
    log.config("config");
    log.fine("fine");

    log.finer("not delegated");
    log.finest("not delegated");

    verify(mockLogger);
    assertEquals(5, logs.getValues().size());
    assertEquals("severe", logs.getValues().get(0).getMessage());
    assertEquals(Level.SEVERE, logs.getValues().get(0).getLevel());
    assertEquals("warning", logs.getValues().get(1).getMessage());
    assertEquals(Level.WARNING, logs.getValues().get(1).getLevel());
    assertEquals("info", logs.getValues().get(2).getMessage());
    assertEquals(Level.INFO, logs.getValues().get(2).getLevel());
    assertEquals("config", logs.getValues().get(3).getMessage());
    assertEquals(Level.CONFIG, logs.getValues().get(3).getLevel());
    assertEquals("fine", logs.getValues().get(4).getMessage());
    assertEquals(Level.FINE, logs.getValues().get(4).getLevel());
  }

  class DelegatingLoggerImpl extends DelegatingLogger {
    public DelegatingLoggerImpl(Logger delegate) {
      super(delegate);
    }

    @Override
    public void log(Level level, String message) {
      log(new LogRecord(level, message));
    }
  }
}