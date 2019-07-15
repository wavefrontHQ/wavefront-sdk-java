package com.wavefront.sdk.common;

import com.wavefront.sdk.common.metrics.WavefrontSdkCounter;
import com.wavefront.sdk.common.metrics.WavefrontSdkMetricsRegistry;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.SocketFactory;

/**
 * Creates a TCP client suitable for the WF proxy. That is: a client which is long-lived and
 * semantically one-way. This client tries persistently to reconnect to the given host and port
 * if a connection is ever broken. If the server (in practice, the WF proxy) sends a TCP FIN or
 * TCP RST, we will treat it as a "broken connection" and just try to connect again on the next
 * call to write(). This means each ReconnectingSocket has a polling thread for the server
 * to listen for connection resets.
 *
 * @author Mori Bellamy (mori@wavefront.com).
 */
public class ReconnectingSocket {
  private static final Logger logger = Logger.getLogger(
      ReconnectingSocket.class.getCanonicalName());

  private static final int
      SERVER_READ_TIMEOUT_MILLIS = 2000,
      SERVER_POLL_INTERVAL_MILLIS = 4000;

  private final String host;
  private final int port;
  private final SocketFactory socketFactory;
  private volatile boolean serverTerminated;
  private final Timer pollingTimer;
  private AtomicReference<Socket> underlyingSocket;
  private AtomicReference<BufferedOutputStream> socketOutputStream;

  private WavefrontSdkCounter writeSuccesses;
  private WavefrontSdkCounter writeErrors;
  private WavefrontSdkCounter flushSuccesses;
  private WavefrontSdkCounter flushErrors;
  private WavefrontSdkCounter resetSuccesses;
  private WavefrontSdkCounter resetErrors;

  /**
   * @throws IOException When we cannot open the remote socket.
   */
  public ReconnectingSocket(String host, int port, SocketFactory socketFactory,
                            WavefrontSdkMetricsRegistry sdkMetricsRegistry, String entityPrefix)
      throws IOException {
    this.host = host;
    this.port = port;
    this.serverTerminated = false;
    this.socketFactory = socketFactory;

    this.underlyingSocket = new AtomicReference<>(socketFactory.createSocket(host, port));
    this.underlyingSocket.get().setSoTimeout(SERVER_READ_TIMEOUT_MILLIS);
    this.socketOutputStream = new AtomicReference<>(new BufferedOutputStream(
        underlyingSocket.get().getOutputStream()));

    this.pollingTimer = new Timer();

    pollingTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        maybeReconnect();
      }
    }, SERVER_POLL_INTERVAL_MILLIS, SERVER_POLL_INTERVAL_MILLIS);

    entityPrefix = entityPrefix == null || entityPrefix.isEmpty() ? "" : entityPrefix + ".";
    writeSuccesses = sdkMetricsRegistry.newCounter(entityPrefix + "write.success");
    writeErrors = sdkMetricsRegistry.newCounter(entityPrefix + "write.errors");
    flushSuccesses = sdkMetricsRegistry.newCounter(entityPrefix + "flush.success");
    flushErrors = sdkMetricsRegistry.newCounter(entityPrefix + "flush.errors");
    resetSuccesses = sdkMetricsRegistry.newCounter(entityPrefix + "reset.success");
    resetErrors = sdkMetricsRegistry.newCounter(entityPrefix + "reset.errors");
  }

  private void maybeReconnect() {
    try {
      byte[] message = new byte[1000];
      int bytesRead;
      try {
        bytesRead = underlyingSocket.get().getInputStream().read(message);
      } catch (IOException e) {
        // Read timeout, just try again later. Important to set SO_TIMEOUT elsewhere.
        return;
      }
      if (bytesRead == -1) {
        serverTerminated = true;
      }
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Cannot poll server for TCP FIN.");
    }
  }

  /**
   * Closes the outputStream best-effort. Tries to re-instantiate the outputStream.
   *
   * @throws IOException          If we cannot close a outputStream we had opened before.
   * @throws UnknownHostException When {@link #host} and {@link #port} are bad.
   */
  private synchronized void resetSocket() throws IOException {
    try {
      try {
        BufferedOutputStream old = socketOutputStream.get();
        if (old != null) old.close();
      } catch (SocketException e) {
        logger.log(Level.INFO, "Could not flush to socket.", e);
      } finally {
        serverTerminated = false;
        try {
          underlyingSocket.getAndSet(socketFactory.createSocket(host, port)).close();
        } catch (SocketException e) {
          logger.log(Level.WARNING, "Could not close old socket.", e);
        }
        underlyingSocket.get().setSoTimeout(SERVER_READ_TIMEOUT_MILLIS);
        socketOutputStream.set(new BufferedOutputStream(underlyingSocket.get().getOutputStream()));
        resetSuccesses.inc();
        logger.log(Level.INFO, String.format("Successfully reset connection to %s:%d", host, port));
      }
    } catch (Exception e) {
      resetErrors.inc();
      throw e;
    }
  }

  /**
   * Try to send the given message. On failure, reset and try again. If _that_ fails,
   * just rethrow the exception.
   *
   * @throws Exception when a single retry is not enough to have a successful write to
   * the remote host.
   */
  public void write(String message) throws Exception {
    try {
      if (serverTerminated) {
        throw new Exception("Remote server terminated.");  // Handled below.
      }
      // Might be NPE due to previously failed call to resetSocket.
      socketOutputStream.get().write(message.getBytes());
      writeSuccesses.inc();
    } catch (Exception e) {
      try {
        logger.log(Level.WARNING, "Attempting to reset socket connection.");
        resetSocket();
        socketOutputStream.get().write(message.getBytes());
        writeSuccesses.inc();
      } catch (Exception e2) {
        writeErrors.inc();
        throw e2;
      }
    }
  }

  /**
   * Flushes the outputStream best-effort. If that fails, we reset the connection.
   */
  public void flush() throws IOException {
    try {
      socketOutputStream.get().flush();
      flushSuccesses.inc();
    } catch (Exception e) {
      flushErrors.inc();
      logger.log(Level.WARNING, "Attempting to reset socket connection.");
      resetSocket();
    }
  }

  public void close() throws IOException {
    try {
      flush();
    } finally {
      pollingTimer.cancel();
      socketOutputStream.get().close();
    }
  }
}
