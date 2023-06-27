package com.wavefront.sdk.common.clients.exceptions;

import java.io.IOException;

/**
 * <p>CompositeIOException class.</p>
 *
 * @author goppegard
 * @version $Id: $Id
 */
public class CompositeIOException extends IOException {
  private final MultiClientIOException exceptions;

  /**
   * <p>Constructor for CompositeIOException.</p>
   *
   * @param exceptions a {@link com.wavefront.sdk.common.clients.exceptions.MultiClientIOException} object
   */
  public CompositeIOException(MultiClientIOException exceptions) {
    super("Client errors were detected and thrown.");
    this.exceptions = exceptions;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    for (Exception exception : exceptions) {
      builder.append('\t').
          append(exception.toString()).
          append('\n');
    }

    return String.format("%s\n{composite exceptions=\n%s}\n%s", this.getClass().getName(),
        builder.toString(), super.toString());
  }
}
