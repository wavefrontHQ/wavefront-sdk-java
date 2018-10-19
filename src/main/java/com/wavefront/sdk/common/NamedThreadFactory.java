package com.wavefront.sdk.common;


import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple thread factory to be used with Executors.newScheduledThreadPool that allows
 * assigning name prefixes to all pooled threads to simplify thread identification during
 * troubleshooting.
 *
 * Created by vasily@wavefront.com on 3/16/17.
 */
public class NamedThreadFactory implements ThreadFactory {
  private final String threadNamePrefix;
  private final AtomicInteger counter = new AtomicInteger();

  /**
   *
   * @param threadNamePrefix  Thread name prefix, cannot be null.
   */
  public NamedThreadFactory(String threadNamePrefix) {
    this.threadNamePrefix = threadNamePrefix;
  }

  /**
   *
   * @param r   The {@link Runnable} that the thread will run, cannot be null.
   * @return a new thread to run {@code r}.
   */
  @Override
  public Thread newThread(Runnable r) {
    Thread toReturn = new Thread(r);
    toReturn.setName(threadNamePrefix + "-" + counter.getAndIncrement());
    return toReturn;
  }
}
