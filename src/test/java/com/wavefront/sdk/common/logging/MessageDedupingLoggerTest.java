package com.wavefront.sdk.common.logging;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link MessageDedupingLogger}
 *
 * @author Han Zhang (zhanghan@vmware.com)
 */
public class MessageDedupingLoggerTest {

  @Test
  public void testLogger() {
    AtomicLong clock = new AtomicLong(System.currentTimeMillis());
    Logger mockLogger = EasyMock.createMock(Logger.class);
    reset(mockLogger);
    expect(mockLogger.getName()).andReturn("loggerName").anyTimes();
    replay(mockLogger);
    MessageDedupingLogger log = new MessageDedupingLogger(mockLogger, 1000, clock::get);
    reset(mockLogger);
    expect(mockLogger.getName()).andReturn("loggerName").anyTimes();
    Capture<LogRecord> logs = Capture.newInstance(CaptureType.ALL);
    mockLogger.log(capture(logs));
    expectLastCall().times(7);
    replay(mockLogger);
    log.severe("msg1");
    log.severe("msg1");
    log.warning("msg1");
    log.info("msg1");
    log.config("msg1");
    log.fine("msg1");
    log.finer("msg1");
    log.finest("msg1");
    log.warning("msg2");
    log.info("msg3");
    log.info("msg3");
    log.severe("msg4");
    log.logWithAlternateKey(Level.FINE, "message 3", "msg3");
    log.logWithAlternateKey(Level.FINE, "message 5", "msg5");
    log.logWithAlternateKey(Level.FINE, "message 5!", "msg5");
    clock.addAndGet(999);
    log.info("msg3");
    log.info("msg3");
    log.severe("msg4");
    clock.addAndGet(1);
    log.fine("msg4");
    log.finer("msg3");
    log.fine("msg3");
    verify(mockLogger);
    assertEquals(7, logs.getValues().size());
    assertEquals("msg1", logs.getValues().get(0).getMessage());
    assertEquals(Level.SEVERE, logs.getValues().get(0).getLevel());
    assertEquals("msg2", logs.getValues().get(1).getMessage());
    assertEquals(Level.WARNING, logs.getValues().get(1).getLevel());
    assertEquals("msg3", logs.getValues().get(2).getMessage());
    assertEquals(Level.INFO, logs.getValues().get(2).getLevel());
    assertEquals("msg4", logs.getValues().get(3).getMessage());
    assertEquals(Level.SEVERE, logs.getValues().get(3).getLevel());
    assertEquals("message 5", logs.getValues().get(4).getMessage());
    assertEquals(Level.FINE, logs.getValues().get(4).getLevel());
    assertEquals("msg4", logs.getValues().get(5).getMessage());
    assertEquals(Level.FINE, logs.getValues().get(5).getLevel());
    assertEquals("msg3", logs.getValues().get(6).getMessage());
    assertEquals(Level.FINER, logs.getValues().get(6).getLevel());
  }
}
