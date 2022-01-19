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
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link MessageDedupingLogger}
 *
 * @author Han Zhang (zhanghan@vmware.com)
 */
public class MessageDedupingLoggerTest {

  @Test
  public void testLogger() {
    Logger mockLogger = EasyMock.createMock(Logger.class);
    reset(mockLogger);
    expect(mockLogger.getName()).andReturn("loggerName").anyTimes();
    replay(mockLogger);
    MessageDedupingLogger log = new MessageDedupingLogger(mockLogger, 1000, 0.1);

    reset(mockLogger);
    expect(mockLogger.getName()).andReturn("loggerName").anyTimes();
    Capture<LogRecord> logs = Capture.newInstance(CaptureType.ALL);
    mockLogger.log(capture(logs));
    expectLastCall().times(5);
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
    log.log("msg3", Level.FINE, "message 3");
    log.log("msg5", Level.FINE, "message 5");
    log.log("msg5", Level.FINE, "message 5!");

    verify(mockLogger);
    assertEquals(5, logs.getValues().size());
    assertEquals("msg1", logs.getValues().get(0).getMessage());
    assertEquals(Level.SEVERE, logs.getValues().get(0).getLevel());
    assertEquals("msg2", logs.getValues().get(1).getMessage());
    assertEquals(Level.WARNING, logs.getValues().get(1).getLevel());
    assertEquals("msg3", logs.getValues().get(2).getMessage());
    assertEquals(Level.INFO, logs.getValues().get(2).getLevel());
    assertEquals("msg4", logs.getValues().get(3).getMessage());
    assertEquals(Level.SEVERE, logs.getValues().get(3).getLevel());
    assertEquals("message 5", logs.getValues().get(4).getMessage());
  }

  @Test
  public void testLoggingThrowables() {
    Logger mockLogger = EasyMock.mock(Logger.class);
    expect(mockLogger.getName()).andReturn("loggerName").anyTimes();
    replay(mockLogger);
    MessageDedupingLogger logger = new MessageDedupingLogger(mockLogger, 1000, 0.1);

    reset(mockLogger);
    expect(mockLogger.getName()).andReturn("loggerName").anyTimes();
    Capture<LogRecord> capturedLogs = Capture.newInstance(CaptureType.ALL);
    mockLogger.log(capture(capturedLogs));
    expectLastCall().times(2);
    replay(mockLogger);

    Exception testException = new Exception("testing");
    logger.log("key1", Level.WARNING, "error", testException);
    logger.log("key1", Level.SEVERE, "error", testException);
    logger.log("key2", Level.SEVERE, "error", testException);

    verify(mockLogger);
    assertEquals(2, capturedLogs.getValues().size());

    LogRecord capLog1 = capturedLogs.getValues().get(0);
    assertEquals("loggerName", capLog1.getLoggerName());
    assertEquals("error", capLog1.getMessage());
    assertEquals(Level.WARNING, capLog1.getLevel());

    LogRecord capLog2 = capturedLogs.getValues().get(1);
    assertEquals("loggerName", capLog2.getLoggerName());
    assertEquals("error", capLog2.getMessage());
    assertEquals(Level.SEVERE, capLog2.getLevel());
    assertEquals(testException, capLog2.getThrown());
  }
}
