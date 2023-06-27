package com.wavefront.sdk.proxy;

import com.wavefront.sdk.common.BufferFlusher;
import com.wavefront.sdk.common.ReconnectingSocket;
import com.wavefront.sdk.common.metrics.WavefrontSdkDeltaCounter;
import com.wavefront.sdk.common.metrics.WavefrontSdkMetricsRegistry;

import javax.net.SocketFactory;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Connection Handler class for sending data to a Wavefront proxy listening on a given port.
 *
 * @author Clement Pang (clement@wavefront.com).
 * @author Vikram Raman (vikram@wavefront.com).
 * @version $Id: $Id
 */
@Deprecated
public class ProxyConnectionHandler implements BufferFlusher, Closeable {

  private final InetSocketAddress address;
  private final SocketFactory socketFactory;
  private volatile ReconnectingSocket reconnectingSocket;

  private final WavefrontSdkMetricsRegistry sdkMetricsRegistry;
  private String entityPrefix;
  private WavefrontSdkDeltaCounter errors;
  private WavefrontSdkDeltaCounter connectErrors;

  ProxyConnectionHandler(InetSocketAddress address, SocketFactory socketFactory,
                         WavefrontSdkMetricsRegistry sdkMetricsRegistry, String entityPrefix) {
    this.address = address;
    this.socketFactory = socketFactory;
    this.reconnectingSocket = null;

    this.sdkMetricsRegistry = sdkMetricsRegistry;
    this.entityPrefix = entityPrefix == null || entityPrefix.isEmpty() ? "" : entityPrefix + ".";
    errors = this.sdkMetricsRegistry.newDeltaCounter(this.entityPrefix + "errors");
    connectErrors = this.sdkMetricsRegistry.newDeltaCounter(this.entityPrefix + "connect.errors");
  }

  synchronized void connect() throws IllegalStateException, IOException {
    if (reconnectingSocket != null) {
      throw new IllegalStateException("Already connected");
    }
    try {
      reconnectingSocket = new ReconnectingSocket(address, socketFactory, sdkMetricsRegistry,
          entityPrefix + "socket");
    } catch (Exception e) {
      connectErrors.inc();
      throw new IOException(e);
    }
  }

  boolean isConnected() {
    return reconnectingSocket != null;
  }

  /** {@inheritDoc} */
  @Override
  public int getFailureCount() {
    return (int)errors.count();
  }

  void incrementFailureCount() {
    errors.inc();
  }

  /** {@inheritDoc} */
  @Override
  public void flush() throws IOException {
    if (isConnected()) {
      reconnectingSocket.flush();
    }
  }

  /** {@inheritDoc} */
  @Override
  public synchronized void close() throws IOException {
    if (isConnected()) {
      reconnectingSocket.close();
      reconnectingSocket = null;
    }
  }

  /**
   * Sends the given data to the WavefrontProxyClient proxy.
   *
   * @param lineData line data in a WavefrontProxyClient supported format
   * @throws Exception If there was failure sending the data
   */
  void sendData(String lineData) throws Exception {
    if (!isConnected()) {
      try {
        connect();
      } catch (IllegalStateException e) {
        // already connected.
      }
    }
    reconnectingSocket.write(lineData);
  }
}
