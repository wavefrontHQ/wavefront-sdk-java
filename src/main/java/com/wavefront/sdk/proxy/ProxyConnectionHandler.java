package com.wavefront.sdk.proxy;

import com.wavefront.sdk.common.BufferFlusher;
import com.wavefront.sdk.common.ReconnectingSocket;
import com.wavefront.sdk.common.annotation.Nullable;
import com.wavefront.sdk.common.metrics.WavefrontSdkCounter;
import com.wavefront.sdk.common.metrics.WavefrontSdkMetricsRegistry;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.SocketFactory;

/**
 * Connection Handler class for sending data to a Wavefront proxy listening on a given port.
 *
 * @author Clement Pang (clement@wavefront.com).
 * @author Vikram Raman (vikram@wavefront.com).
 */
public class ProxyConnectionHandler implements BufferFlusher, Closeable {

  private final InetSocketAddress address;
  private final SocketFactory socketFactory;
  private volatile ReconnectingSocket reconnectingSocket;
  private final AtomicInteger failures;

  @Nullable
  private final WavefrontSdkMetricsRegistry sdkMetricsRegistry;
  @Nullable
  private String metricPrefix;
  @Nullable
  private WavefrontSdkCounter connectErrors;

  ProxyConnectionHandler(InetSocketAddress address, SocketFactory socketFactory,
                                   @Nullable WavefrontSdkMetricsRegistry sdkMetricsRegistry,
                                   @Nullable String metricPrefix) {
    this.address = address;
    this.socketFactory = socketFactory;
    this.reconnectingSocket = null;
    failures = new AtomicInteger();

    this.sdkMetricsRegistry = sdkMetricsRegistry;
    this.metricPrefix = metricPrefix == null || metricPrefix.isEmpty() ? "" : metricPrefix + ".";
    if (this.sdkMetricsRegistry != null) {
      this.sdkMetricsRegistry.newGauge(this.metricPrefix + "errors.count", this::getFailureCount);
      connectErrors = this.sdkMetricsRegistry.newCounter(this.metricPrefix + "connect.errors");
    }
  }

  synchronized void connect() throws IllegalStateException, IOException {
    if (reconnectingSocket != null) {
      throw new IllegalStateException("Already connected");
    }
    try {
      reconnectingSocket = new ReconnectingSocket(address.getHostName(), address.getPort(),
          socketFactory, sdkMetricsRegistry, metricPrefix + "socket");
    } catch (Exception e) {
      if (connectErrors != null) {
        connectErrors.inc();
      }
      throw new IOException(e);
    }
  }

  boolean isConnected() {
    return reconnectingSocket != null;
  }

  @Override
  public int getFailureCount() {
    return failures.get();
  }

  void incrementFailureCount() {
    failures.incrementAndGet();
  }

  @Override
  public void flush() throws IOException {
    if (isConnected()) {
      reconnectingSocket.flush();
    }
  }

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
