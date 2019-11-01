package com.wavefront.sdk.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.wavefront.sdk.common.WavefrontSender;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class ProxyTest {
    private final int NUM_THREADS = 10;
    private final int NUM_ITERATIONS = 1000;

    private final int THREAD_WAIT_ITERATIONS = 10;

    private final class MockServer implements Runnable {
        public void run() {
            try {
                ServerSocket s = new ServerSocket(12345);
                Socket cs = s.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(cs.getInputStream()));
                int n = 0;
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println(line);
                    n++;
                }
                assertEquals(NUM_ITERATIONS * NUM_THREADS, n, "Wrong number of messages received");
            } catch(IOException e) {
                fail(e);
            }
        }
    }

    private static final Thread[] getAllThreads() {
        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
        ThreadGroup parentGroup;
        while ((parentGroup = rootGroup.getParent()) != null) {
            rootGroup = parentGroup;
        }
        Thread[] threads = new Thread[rootGroup.activeCount()];
        while (rootGroup.enumerate(threads, true ) == threads.length) {
            threads = new Thread[threads.length * 2];
        }
        return threads;
    }

    @Test
    public void testProxyRoundtrip() {
        try {
            // Take a snapshot of active threads before we start the client
            Thread[] threads = getAllThreads();
            Map<Long, Thread> tMap = new HashMap<>();
            for(Thread t : threads) {
                if(t == null) {
                    continue;
                }
                tMap.put(t.getId(), t);
            }
            Thread server = new Thread(new MockServer());
            server.setDaemon(false);
            server.start();
            WavefrontProxyClient.Builder b = new WavefrontProxyClient.Builder("localhost");
            b.metricsPort(12345);
            final WavefrontSender s = b.build();
            final Semaphore semaphore = new Semaphore(0);
            for(int i = 0; i < NUM_THREADS; ++i) {
                new Thread(() -> {
                    for (int j = 0; j < NUM_ITERATIONS; ++j) {
                        try {
                            s.sendMetric("dummy", 1.0, System.currentTimeMillis(), "dummy", new HashMap<>());
                        } catch(IOException e) {
                            fail(e);
                        }
                    }
                    semaphore.release();
                }).start();
            }

           semaphore.acquire(NUM_THREADS);
            s.close();

            // Wait for all new non-daemon threads to terminate (or timeout)
            int n = 0;
            for(;;) {
                threads = getAllThreads();
                int newT = 0;
                for(Thread t : threads) {
                    if(t == null) {
                        break;
                    }
                    if(!tMap.containsKey(t.getId()) && !t.isDaemon()) {
                        ++newT;
                        System.out.println("Non-daemon thread still running: " + t.toString());
                    }
                }
                if(newT > 0) {
                    n++;
                    if(n >= THREAD_WAIT_ITERATIONS) {
                        fail("Gave up waiting for threads to exit");
                    }
                    Thread.sleep(1000);
                } else {
                    break;
                }
            }
        } catch(IOException e) {
            fail(e);
        }
        catch(InterruptedException e) {
            fail(e);
        }
    }
}
