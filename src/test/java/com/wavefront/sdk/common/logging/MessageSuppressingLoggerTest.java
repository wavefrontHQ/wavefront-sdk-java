package com.wavefront.sdk.common.logging;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link MessageSuppressingLogger}.
 *
 * @author Shipeng Xie (xshipeng@vmware.com).
 */
public class MessageSuppressingLoggerTest {
  @Test
  public void testLogger() throws InterruptedException {
    Logger logger = EasyMock.mock(Logger.class);
    EasyMock.expect(logger.getName()).andReturn("TestLogger").anyTimes();
    Capture<LogRecord> logs = Capture.newInstance(CaptureType.ALL);
    logger.log(EasyMock.capture(logs));
    EasyMock.expectLastCall().times(3);
    EasyMock.replay(logger);

    MessageSuppressingLogger messageSuppressingLogger = new MessageSuppressingLogger(logger, 1L, TimeUnit.SECONDS);
    messageSuppressingLogger.severe("msg1");
    messageSuppressingLogger.warning("msg2");
    messageSuppressingLogger.info("msg3");
    messageSuppressingLogger.log("https://ex.wavefront.com", Level.SEVERE, "msg4");
    messageSuppressingLogger.log("http://localhost:8080", Level.SEVERE, "msg5");

    messageSuppressingLogger.severe("msg1");
    messageSuppressingLogger.warning("msg2");
    messageSuppressingLogger.info("msg3");
    messageSuppressingLogger.log("https://ex.wavefront.com", Level.SEVERE, "msg4");
    messageSuppressingLogger.log("http://localhost:8080", Level.SEVERE, "msg5");

    TimeUnit.SECONDS.sleep(1);

    messageSuppressingLogger.severe("msg1");
    messageSuppressingLogger.log(Level.WARNING, "msg2");
    messageSuppressingLogger.reset("msg3");
    messageSuppressingLogger.info("msg3");
    messageSuppressingLogger.log("https://ex.wavefront.com", Level.SEVERE, "msg6");
    messageSuppressingLogger.reset("http://localhost:8080");
    messageSuppressingLogger.log("http://localhost:8080", Level.SEVERE, "msg7");
    EasyMock.verify(logger);

    assertEquals(3, logs.getValues().size());
    assertEquals("msg1", logs.getValues().get(0).getMessage());
    assertEquals(Level.SEVERE, logs.getValues().get(0).getLevel());
    assertEquals("com.wavefront.sdk.common.logging.MessageSuppressingLoggerTest",
        logs.getValues().get(0).getSourceClassName());
    assertEquals("testLogger", logs.getValues().get(0).getSourceMethodName());
    assertEquals(Level.WARNING, logs.getValues().get(1).getLevel());
    assertEquals("msg2", logs.getValues().get(1).getMessage());
    assertEquals("com.wavefront.sdk.common.logging.MessageSuppressingLoggerTest",
        logs.getValues().get(1).getSourceClassName());
    assertEquals("testLogger", logs.getValues().get(1).getSourceMethodName());
    assertEquals(Level.SEVERE, logs.getValues().get(2).getLevel());
    assertEquals("msg6", logs.getValues().get(2).getMessage());
    assertEquals("com.wavefront.sdk.common.logging.MessageSuppressingLoggerTest",
        logs.getValues().get(2).getSourceClassName());
    assertEquals("testLogger", logs.getValues().get(2).getSourceMethodName());
  }
}
