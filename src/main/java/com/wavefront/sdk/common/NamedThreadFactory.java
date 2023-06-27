package com.wavefront.sdk.common;


import com.wavefront.sdk.common.annotation.NonNull;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple thread factory to be used with Executors.newScheduledThreadPool that allows
 * assigning name prefixes to all pooled threads to simplify thread identification during
 * troubleshooting.
 *
 * Created by vasily@wavefront.com on 3/16/17.
 *
 * @author goppegard
 * @version $Id: $Id
 */
public class NamedThreadFactory implements ThreadFactory {
  private final String threadNamePrefix;
  private final AtomicInteger counter = new AtomicInteger();
  private final AtomicBoolean isDaemon = new AtomicBoolean(false);

  /**
   * <p>Constructor for NamedThreadFactory.</p>
   *
   * @param threadNamePrefix a {@link java.lang.String} object
   */
  public NamedThreadFactory(@NonNull String threadNamePrefix) {
    this.threadNamePrefix = threadNamePrefix;
  }

  /**
   * <p>setDaemon.</p>
   *
   * @param isDaemon a boolean
   * @return a {@link com.wavefront.sdk.common.NamedThreadFactory} object
   */
  public NamedThreadFactory setDaemon(boolean isDaemon) {
    this.isDaemon.set(isDaemon);
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public Thread newThread(@NonNull Runnable r) {
    Thread toReturn = new Thread(r);
    if (this.isDaemon.get()) {
      toReturn.setDaemon(true);
    }
    toReturn.setName(threadNamePrefix + "-" + counter.getAndIncrement());
    return toReturn;
  }
}
