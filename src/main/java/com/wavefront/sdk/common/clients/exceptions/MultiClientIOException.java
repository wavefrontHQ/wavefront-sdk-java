package com.wavefront.sdk.common.clients.exceptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <p>MultiClientIOException class.</p>
 *
 * @author goppegard
 * @version $Id: $Id
 */
public class MultiClientIOException implements Iterable<IOException> {
  private final List<IOException> exceptions = new ArrayList<>();

  /** {@inheritDoc} */
  @Override
  public Iterator<IOException> iterator() {
    return exceptions.iterator();
  }

  /**
   * <p>add.</p>
   *
   * @param exception a {@link java.io.IOException} object
   */
  public void add(IOException exception) {
    this.exceptions.add(exception);
  }

  /**
   * <p>checkAndThrow.</p>
   *
   * @throws com.wavefront.sdk.common.clients.exceptions.CompositeIOException if any.
   */
  public void checkAndThrow() throws CompositeIOException {
    if (!exceptions.isEmpty())
      throw new CompositeIOException(this);
  }
}
