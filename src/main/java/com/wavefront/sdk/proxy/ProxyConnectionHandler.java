package com.wavefront.sdk.proxy;

import com.wavefront.sdk.common.BufferFlusher;
import com.wavefront.sdk.common.ReconnectingSocket;

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

  protected ProxyConnectionHandler(InetSocketAddress address, SocketFactory socketFactory) {
    this.address = address;
    this.socketFactory = socketFactory;
    this.reconnectingSocket = null;
    failures = new AtomicInteger();
  }

  public synchronized void connect() throws IllegalStateException, IOException {
    if (reconnectingSocket != null) {
      throw new IllegalStateException("Already connected");
    }
    try {
      reconnectingSocket = new ReconnectingSocket(address.getHostName(), address.getPort(),
          socketFactory);
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  public boolean isConnected() {
    return reconnectingSocket != null;
  }

  @Override
  public int getFailureCount() {
    return failures.get();
  }

  public void incrementFailureCount() {
    failures.incrementAndGet();
  }

  @Override
  public void flush() throws IOException {
    if (reconnectingSocket != null) {
      reconnectingSocket.flush();
    }
  }

  @Override
  public synchronized void close() throws IOException {
    if (reconnectingSocket != null) {
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
  protected void sendData(String lineData) throws Exception {
    if (reconnectingSocket == null) {
      try {
        connect();
      } catch (IllegalStateException e) {
        // already connected.
      }
    }
    reconnectingSocket.write(lineData);
  }
}
