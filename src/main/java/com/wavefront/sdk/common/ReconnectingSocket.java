package com.wavefront.sdk.common;

import com.wavefront.sdk.common.metrics.WavefrontSdkCounter;
import com.wavefront.sdk.common.metrics.WavefrontSdkMetricsRegistry;

import javax.net.SocketFactory;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

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
      SERVER_CONNECT_TIMEOUT_MILLIS = 5000,
      SERVER_READ_TIMEOUT_MILLIS = 2000,
      SERVER_POLL_INTERVAL_MILLIS = 4000;

  private final InetSocketAddress address;
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
   * Attempts to open a connected socket to the specified host and port.
   *
   * @param host                The name of the remote host.
   * @param port                The remote port.
   * @param socketFactory       The {@link SocketFactory} used to create the underlying socket.
   * @param sdkMetricsRegistry  The {@link WavefrontSdkMetricsRegistry} for internal metrics.
   * @param entityPrefix        A prefix for internal metrics pertaining to this instance.
   * @throws IOException When we cannot open the remote socket.
   */
  public ReconnectingSocket(String host, int port, SocketFactory socketFactory,
                            WavefrontSdkMetricsRegistry sdkMetricsRegistry, String entityPrefix)
      throws IOException {
    this(new InetSocketAddress(host, port), socketFactory, sdkMetricsRegistry, entityPrefix);
  }

  /**
   * Attempts to open a connected socket to the specified address.
   *
   * @param address             The {@link InetSocketAddress} of the server to connect to.
   * @param socketFactory       The {@link SocketFactory} used to create the underlying socket.
   * @param sdkMetricsRegistry  The {@link WavefrontSdkMetricsRegistry} for internal metrics.
   * @param entityPrefix        A prefix for internal metrics pertaining to this instance.
   * @throws IOException When we cannot open the remote socket.
   */
  public ReconnectingSocket(InetSocketAddress address, SocketFactory socketFactory,
                            WavefrontSdkMetricsRegistry sdkMetricsRegistry, String entityPrefix)
      throws IOException {
    this.address = address;
    this.serverTerminated = false;
    this.socketFactory = socketFactory;

    this.underlyingSocket = new AtomicReference<>(createAndConnectSocket());
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

  private Socket createAndConnectSocket() throws IOException {
    Socket socket = socketFactory.createSocket();
    try {
      socket.connect(address, SERVER_CONNECT_TIMEOUT_MILLIS);
      socket.setSoTimeout(SERVER_READ_TIMEOUT_MILLIS);
    } catch (IOException e) {
      try {
        socket.close();
      } catch (IOException ce) {
        e.addSuppressed(ce);
      }
      throw e;
    }
    return socket;
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
   * @throws IOException If we cannot close a outputStream we had opened before.
   */
  private synchronized void resetSocket() throws IOException {
    try {
      try {
        BufferedOutputStream old = socketOutputStream.get();
        if (old != null) old.close();
      } catch (IOException e) {
        logger.log(Level.INFO, "Could not flush to socket.", e);
      } finally {
        serverTerminated = false;
        Socket newSocket = createAndConnectSocket();
        try {
          underlyingSocket.getAndSet(newSocket).close();
        } catch (IOException e) {
          logger.log(Level.WARNING, "Could not close old socket.", e);
        }
        socketOutputStream.set(new BufferedOutputStream(underlyingSocket.get().getOutputStream()));
        resetSuccesses.inc();
        logger.log(Level.INFO, String.format("Successfully reset connection to %s:%d",
            address.getHostName(), address.getPort()));
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
        String warningMsg =
            "Unable to write data to " + address.getHostName() + ":" + address.getPort() +
            " (" + e.getMessage() +  "), reconnecting ...";
        if (logger.isLoggable(Level.FINE)) {
          logger.log(Level.WARNING, warningMsg, e);
        } else {
          logger.warning(warningMsg);
        }
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
      String warningMsg =
          "Unable to flush data to " + address.getHostName() + ":" + address.getPort() +
          " (" + e.getMessage() +  "), reconnecting ...";
      if (logger.isLoggable(Level.FINE)) {
        logger.log(Level.WARNING, warningMsg, e);
      } else {
        logger.warning(warningMsg);
      }
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
